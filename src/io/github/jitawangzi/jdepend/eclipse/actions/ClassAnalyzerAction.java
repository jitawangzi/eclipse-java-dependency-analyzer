package io.github.jitawangzi.jdepend.eclipse.actions;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;

import io.github.jitawangzi.jdepend.eclipse.config.PluginConfig;
import io.github.jitawangzi.jdepend.eclipse.dialogs.ConfigurationDialog;
import io.github.jitawangzi.jdepend.eclipse.utils.EclipseProjectUtils;

/**
 * 类分析器动作
 */
public class ClassAnalyzerAction implements IObjectActionDelegate {
    
    private Shell shell;
    private ICompilationUnit selectedUnit;
    
    @Override
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
        shell = targetPart.getSite().getShell();
    }
    
    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        selectedUnit = null;
        if (selection instanceof IStructuredSelection) {
            IStructuredSelection structuredSelection = (IStructuredSelection) selection;
            Object firstElement = structuredSelection.getFirstElement();
            
            if (firstElement instanceof IFile) {
                IFile file = (IFile) firstElement;
                if ("java".equals(file.getFileExtension())) {
                    selectedUnit = JavaCore.createCompilationUnitFrom(file);
                }
            } else if (firstElement instanceof ICompilationUnit) {
                selectedUnit = (ICompilationUnit) firstElement;
            }
        }
    }
    
    @Override
    public void run(IAction action) {
        if (selectedUnit == null) {
            MessageDialog.openError(shell, "Error", "Please select a Java class file.");
            return;
        }
        
        try {
            debugInfo("=== ClassAnalyzerAction 开始执行 ===");
            
            // 准备默认配置
            PluginConfig config = new PluginConfig();
            
            // 从选中的类推断配置
            String className = EclipseProjectUtils.getFullyQualifiedClassName(selectedUnit);
            String projectRoot = EclipseProjectUtils.getProjectRootPath(selectedUnit.getResource());
            String packagePrefixes = EclipseProjectUtils.inferPackagePrefixes(selectedUnit);
            String sourceDirectories = EclipseProjectUtils.getSourceDirectories(selectedUnit);
            
            config.setMainClass(className);
            config.setProjectRoot(projectRoot);
            config.setProjectPackagePrefixes(packagePrefixes);
            config.setSourceDirectories(sourceDirectories);
            
            // 设置输出文件到项目目录
            config.setOutputFileWithProjectPath(projectRoot, "dependency-analysis.md");
            
            debugInfo("推断配置: 主类=" + className + ", 项目根路径=" + projectRoot + 
                     ", 包前缀=" + packagePrefixes + ", 输出文件=" + config.getAbsoluteOutputFile());
            
            // 显示配置对话框
            ConfigurationDialog dialog = new ConfigurationDialog(shell, config, true, className);
            if (dialog.open() == Window.OK) {
                // 获取配置并执行分析
                PluginConfig finalConfig = dialog.getConfig();
                executeClassAnalysis(finalConfig);
            }
            
        } catch (Exception e) {
            String errorMsg = "Failed to analyze class: " + e.getMessage();
            debugError(errorMsg, e);
            MessageDialog.openError(shell, "Error", errorMsg);
        }
    }
    
    private void executeClassAnalysis(PluginConfig config) {
        debugInfo("开始执行类分析，主类: " + config.getMainClass());
        
        // 设置系统属性
        setSystemPropertiesFromConfig(config);
        
        // 在后台线程中执行分析
        Thread analysisThread = new Thread(() -> {
            try {
                executeAnalysisWithDebug(config);
                
            } catch (Exception e) {
                String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                debugError("分析失败: " + errorMsg, e);
                
                shell.getDisplay().asyncExec(() -> {
                    MessageDialog.openError(shell, "Analysis Failed", 
                        "分析失败: " + errorMsg + "\n\n详细信息请查看控制台输出");
                });
            }
        });
        
        analysisThread.setName("ClassAnalysis-Thread");
        analysisThread.start();
    }
    
    private void executeAnalysisWithDebug(PluginConfig config) {
        try {
            // 获取插件的类加载器
            ClassLoader pluginClassLoader = this.getClass().getClassLoader();
            debugInfo("插件类加载器: " + pluginClassLoader.getClass().getName());
            // 动态扫描 lib/ 下所有 JAR
            java.util.List<java.net.URL> validUrls = new java.util.ArrayList<>();
            java.net.URL pluginJarUrl = getClass().getProtectionDomain().getCodeSource().getLocation();  // 插件 JAR 的 URL
            debugInfo("插件 JAR URL: " + pluginJarUrl);
            
            try (java.io.InputStream jarStream = pluginJarUrl.openStream();
                 java.util.zip.ZipInputStream zipInput = new java.util.zip.ZipInputStream(jarStream)) {
                java.util.zip.ZipEntry entry;
                while ((entry = zipInput.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name.startsWith("lib/") && name.endsWith(".jar") && !entry.isDirectory()) {
                        // 构建内嵌 JAR 的 URL
                        java.net.URL innerJarUrl = new java.net.URL("jar:" + pluginJarUrl + "!/" + name);
                        validUrls.add(innerJarUrl);
                        debugInfo("找到嵌入 JAR: " + name + " -> " + innerJarUrl);
                    }
                }
            }
            
            if (validUrls.isEmpty()) {
                throw new Exception("没有找到任何必需的JAR文件！请检查插件的lib目录");
            }
            
            debugInfo("共找到 " + validUrls.size() + " 个JAR文件");
            
            // 创建URLClassLoader
            java.net.URLClassLoader customClassLoader = new java.net.URLClassLoader(
                validUrls.toArray(new java.net.URL[0]), pluginClassLoader);
            
            // 设置线程的类加载器
            Thread.currentThread().setContextClassLoader(customClassLoader);
            
            // 尝试加载ClassAnalyzer类
            String targetClassName = "io.github.jitawangzi.jdepend.ClassAnalyzer";
            Class<?> classAnalyzerClass = customClassLoader.loadClass(targetClassName);
            debugInfo("成功加载分析器类: " + classAnalyzerClass.getName());
            
            // 获取main方法
            java.lang.reflect.Method mainMethod = classAnalyzerClass.getMethod("main", String[].class);
            
            // 保存和设置工作目录
            String originalUserDir = System.getProperty("user.dir");
            String projectRoot = config.getProjectRoot();
            if (projectRoot != null && !projectRoot.trim().isEmpty()) {
                System.setProperty("user.dir", projectRoot);
                debugInfo("设置工作目录为: " + projectRoot);
            }
            
            try {
                // 调用main方法
                debugInfo("开始执行分析...");
                mainMethod.invoke(null, (Object) new String[0]);
                debugInfo("分析执行完成");
                
            } finally {
                // 恢复原始工作目录
                System.setProperty("user.dir", originalUserDir);
                debugInfo("恢复工作目录为: " + originalUserDir);
            }
            
            // 检查文件生成结果
            checkAndProcessOutputFile(config);
            
        } catch (Exception e) {
            debugError("执行分析时出错", e);
            throw new RuntimeException("Analysis execution failed", e);
        }
    }
    
    private void checkAndProcessOutputFile(PluginConfig config) {
        String absoluteOutputFile = config.getAbsoluteOutputFile();
        java.io.File file = new java.io.File(absoluteOutputFile);
        
        debugInfo("检查输出文件: " + absoluteOutputFile);
        
        // 如果目标位置没有文件，检查项目根目录下是否有
        if (!file.exists()) {
            debugInfo("目标位置没有文件，检查项目根目录...");
            java.io.File projectDir = new java.io.File(config.getProjectRoot());
            if (projectDir.exists()) {
                java.io.File[] projectFiles = projectDir.listFiles();
                if (projectFiles != null) {
                    for (java.io.File f : projectFiles) {
                        if (f.getName().endsWith(".md") && 
                            (f.getName().equals("dependency-analysis.md") || f.getName().equals("output.md"))) {
                            try {
                                java.nio.file.Files.copy(f.toPath(), file.toPath(), 
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                debugInfo("文件已移动到目标位置: " + file.getAbsolutePath());
                                f.delete();
                                break;
                            } catch (Exception e) {
                                debugError("移动文件失败", e);
                            }
                        }
                    }
                }
            }
        }
        
        // 最终结果检查
        final boolean fileExists = file.exists();
        final long fileSize = fileExists ? file.length() : 0;
        
        if (fileExists) {
            debugInfo("分析完成！文件大小: " + fileSize + " bytes");
        } else {
            debugInfo("警告: 输出文件未生成，请检查配置和日志");
        }
        
        // 在UI线程中显示结果
        shell.getDisplay().asyncExec(() -> {
            if (fileExists) {
                MessageDialog.openInformation(shell, "Analysis Completed", 
                    "类依赖分析完成！\n\n" +
                    "输出文件: " + file.getName() + "\n" +
                    "文件大小: " + (fileSize / 1024) + " KB\n" +
                    "位置: " + file.getParent());
            } else {
                MessageDialog.openWarning(shell, "Analysis Completed", 
                    "分析已执行完成，但输出文件未生成。\n\n" +
                    "请检查控制台输出了解详细信息。");
            }
        });
    }
    
    private void setSystemPropertiesFromConfig(PluginConfig config) {
        debugInfo("设置系统属性配置...");
        
        // 类分析配置
        setAndDebugProperty("main.class", config.getMainClass());
        setAndDebugProperty("project.root", config.getProjectRoot());
        setAndDebugProperty("project.package.prefixes", config.getProjectPackagePrefixes());
        setAndDebugProperty("method.body.max.depth", String.valueOf(config.getMethodBodyMaxDepth()));
        setAndDebugProperty("keep.only.referenced.methods", String.valueOf(config.isKeepOnlyReferencedMethods()));
        setAndDebugProperty("show.removed.methods", String.valueOf(config.isShowRemovedMethods()));
        setAndDebugProperty("source.directories", config.getSourceDirectories());
        setAndDebugProperty("directory.mode.enabled", "false");
        
        // 通用配置
        setAndDebugProperty("output.file", config.getAbsoluteOutputFile());
        setAndDebugProperty("max.depth", String.valueOf(config.getMaxDepth()));
        setAndDebugProperty("excluded.packages", config.getExcludedPackages());
        setAndDebugProperty("method.exceptions", config.getMethodExceptions());
        setAndDebugProperty("content.size.threshold", String.valueOf(config.getContentSizeThreshold()));
        setAndDebugProperty("omit.bean.methods", String.valueOf(config.isOmitBeanMethods()));
        setAndDebugProperty("show.omitted.accessors", String.valueOf(config.isShowOmittedAccessors()));
        setAndDebugProperty("import.skip.enabled", String.valueOf(config.isImportSkipEnabled()));
        setAndDebugProperty("import.skip.prefixes", config.getImportSkipPrefixes());
        setAndDebugProperty("import.keep.prefixes", config.getImportKeepPrefixes());
        setAndDebugProperty("show.error.stacktrace", String.valueOf(config.isShowErrorStacktrace()));
    }
    
    private void setAndDebugProperty(String key, String value) {
        System.setProperty(key, value);
        debugInfo("  " + key + " = " + value);
    }
    
    private void debugInfo(String message) {
        String debugMsg = "[ClassAnalyzer] " + message;
        System.out.println(debugMsg);
        
        // 输出到Eclipse控制台
        try {
            org.eclipse.ui.console.IConsoleManager consoleManager = 
                org.eclipse.ui.console.ConsolePlugin.getDefault().getConsoleManager();
            
            org.eclipse.ui.console.MessageConsole targetConsole = null;
            org.eclipse.ui.console.IConsole[] consoles = consoleManager.getConsoles();
            
            for (org.eclipse.ui.console.IConsole existingConsole : consoles) {
                if ("Java Dependency Analyzer".equals(existingConsole.getName())) {
                    targetConsole = (org.eclipse.ui.console.MessageConsole) existingConsole;
                    break;
                }
            }
            
            if (targetConsole == null) {
                targetConsole = new org.eclipse.ui.console.MessageConsole("Java Dependency Analyzer", null);
                consoleManager.addConsoles(new org.eclipse.ui.console.IConsole[]{targetConsole});
            }
            
            org.eclipse.ui.console.MessageConsoleStream stream = targetConsole.newMessageStream();
            stream.println(debugMsg);
            
            final org.eclipse.ui.console.MessageConsole finalConsole = targetConsole;
            shell.getDisplay().asyncExec(() -> {
                consoleManager.showConsoleView(finalConsole);
            });
            
        } catch (Exception e) {
            System.err.println("Failed to write to Eclipse console: " + e.getMessage());
        }
    }
    
    private void debugError(String message, Exception e) {
        String errorMsg = "[ClassAnalyzer-ERROR] " + message;
        System.err.println(errorMsg);
        if (e != null) {
            e.printStackTrace();
        }
        debugInfo("ERROR: " + message + (e != null ? " - " + e.getMessage() : ""));
    }
}

