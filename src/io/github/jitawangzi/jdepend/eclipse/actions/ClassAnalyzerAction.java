package io.github.jitawangzi.jdepend.eclipse.actions;

import java.io.PrintWriter;
import java.io.StringWriter;

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
 * 类分析器动作 - 使用系统属性传参版本
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
            
            debugInfo("配置信息:\n" +
                "主类: " + className + "\n" +
                "项目根路径: " + projectRoot + "\n" +
                "包前缀: " + packagePrefixes + "\n" +
                "源码目录: " + sourceDirectories + "\n" +
                "输出文件: " + config.getAbsoluteOutputFile());
            
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
        debugInfo("=== 开始执行类分析 ===");
        
        // 设置系统属性
        setSystemPropertiesFromConfig(config, true);
        
        // 在后台线程中执行分析
        Thread analysisThread = new Thread(() -> {
            try {
                debugInfo("后台线程开始执行");
                executeAnalysisWithDebug(config);
                
            } catch (Exception e) {
                String errorMsg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
                debugError("分析失败: " + errorMsg, e);
                
                shell.getDisplay().asyncExec(() -> {
                    MessageDialog.openError(shell, "Analysis Failed", 
                        "分析失败:\n" + errorMsg + "\n\n详细信息请查看控制台输出");
                });
            }
        });
        
        analysisThread.setName("ClassAnalysis-Thread");
        analysisThread.start();
    }
    
    private void executeAnalysisWithDebug(PluginConfig config) {
        try {
            debugInfo("=== 开始类加载调试 ===");
            
            // 获取插件的类加载器
            ClassLoader pluginClassLoader = this.getClass().getClassLoader();
            debugInfo("插件类加载器: " + pluginClassLoader.getClass().getName());
            
            // 检查所有需要的jar文件
            String[] requiredJars = {
                "lib/jdepend-analyzer.jar",
                "lib/javaparser-core-3.25.10.jar",
                "lib/slf4j-api-2.0.12.jar",
                "lib/logback-classic-1.5.6.jar",
                "lib/logback-core-1.5.6.jar"
            };
            
            java.util.List<java.net.URL> validUrls = new java.util.ArrayList<>();
            
            for (String jarPath : requiredJars) {
                java.net.URL jarUrl = pluginClassLoader.getResource(jarPath);
                debugInfo("检查JAR: " + jarPath + " -> " + (jarUrl != null ? jarUrl.toString() : "NOT FOUND"));
                
                if (jarUrl != null) {
                    validUrls.add(jarUrl);
                } else {
                    debugInfo("警告: 找不到JAR文件: " + jarPath);
                }
            }
            
            if (validUrls.isEmpty()) {
                throw new Exception("没有找到任何必需的JAR文件！请检查插件的lib目录");
            }
            
            debugInfo("找到 " + validUrls.size() + " 个JAR文件");
            
            // 创建URLClassLoader
            java.net.URLClassLoader customClassLoader = new java.net.URLClassLoader(
                validUrls.toArray(new java.net.URL[0]), pluginClassLoader);
            
            debugInfo("创建自定义类加载器: " + customClassLoader.getClass().getName());
            
            // 设置线程的类加载器
            Thread.currentThread().setContextClassLoader(customClassLoader);
            debugInfo("设置线程类加载器");
            
            // 尝试加载ClassAnalyzer类
            String targetClassName = "io.github.jitawangzi.jdepend.ClassAnalyzer";
            debugInfo("尝试加载类: " + targetClassName);
            
            Class<?> classAnalyzerClass = null;
            try {
                classAnalyzerClass = customClassLoader.loadClass(targetClassName);
                debugInfo("成功加载类: " + classAnalyzerClass.getName());
                debugInfo("类加载器: " + classAnalyzerClass.getClassLoader().getClass().getName());
            } catch (ClassNotFoundException e) {
                debugError("找不到类: " + targetClassName, e);
                
                // 尝试列出jar文件中的类
                debugJarContents(validUrls.get(0)); // 检查第一个jar的内容
                throw e;
            }
            
            // 获取main方法
            java.lang.reflect.Method mainMethod = null;
            try {
                mainMethod = classAnalyzerClass.getMethod("main", String[].class);
                debugInfo("成功获取main方法: " + mainMethod.getName());
            } catch (NoSuchMethodException e) {
                debugError("找不到main方法", e);
                
                // 列出所有可用方法
                java.lang.reflect.Method[] methods = classAnalyzerClass.getDeclaredMethods();
                debugInfo("类中的所有方法:");
                for (java.lang.reflect.Method method : methods) {
                    debugInfo("  - " + method.getName() + " " + java.util.Arrays.toString(method.getParameterTypes()));
                }
                throw e;
            }
            
            debugInfo("=== 测试Java分析工程 ===");
            
            // 保存原始工作目录
            String originalUserDir = System.getProperty("user.dir");
            debugInfo("原始工作目录: " + originalUserDir);
            
            // 设置工作目录到项目根目录
            String projectRoot = config.getProjectRoot();
            if (projectRoot != null && !projectRoot.trim().isEmpty()) {
                System.setProperty("user.dir", projectRoot);
                debugInfo("设置工作目录为: " + projectRoot);
            }
            
            // 检查所有系统属性是否正确设置
            debugInfo("系统属性检查:");
            java.util.Properties props = System.getProperties();
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith("main.class") || key.startsWith("project.") || 
                    key.startsWith("output.") || key.startsWith("method.") ||
                    key.startsWith("excluded.") || key.startsWith("import.") ||
                    key.startsWith("directory.") || key.startsWith("source.") ||
                    key.startsWith("keep.") || key.startsWith("show.") ||
                    key.startsWith("omit.") || key.startsWith("content.") ||
                    key.startsWith("simplify.") || key.equals("user.dir")) {
                    debugInfo("  " + key + " = " + props.getProperty(key));
                }
            }
            
            debugInfo("=== 准备调用main方法 ===");
            String absoluteOutputFile = config.getAbsoluteOutputFile();
            debugInfo("输出文件: " + absoluteOutputFile);
            
            // 显示即将开始分析的信息
            shell.getDisplay().asyncExec(() -> {
                MessageDialog.openInformation(shell, "Analysis Started", 
                    "开始执行类分析...\n" +
                    "主类: " + config.getMainClass() + "\n" +
                    "输出文件: " + absoluteOutputFile + "\n\n" +
                    "详细调试信息请查看控制台");
            });
            
            try {
                // 调用main方法
                mainMethod.invoke(null, (Object) new String[0]);
                debugInfo("=== main方法调用完成 ===");
                
            } finally {
                // 恢复原始工作目录
                System.setProperty("user.dir", originalUserDir);
                debugInfo("恢复工作目录为: " + originalUserDir);
            }
            
            // 检查文件是否生成
            debugInfo("检查输出文件: " + absoluteOutputFile);
            
            java.io.File file = new java.io.File(absoluteOutputFile);
            debugInfo("文件绝对路径: " + file.getAbsolutePath());
            debugInfo("文件是否存在: " + file.exists());
            
            // 如果目标位置没有文件，检查项目根目录下是否有
            if (!file.exists()) {
                debugInfo("目标位置没有文件，检查项目根目录...");
                java.io.File projectDir = new java.io.File(config.getProjectRoot());
                if (projectDir.exists()) {
                    java.io.File[] projectFiles = projectDir.listFiles();
                    if (projectFiles != null) {
                        for (java.io.File f : projectFiles) {
                            if (f.getName().endsWith(".md")) {
                                debugInfo("  项目根目录中发现MD文件: " + f.getName() + " (" + f.length() + " bytes)");
                                
                                // 如果是我们期望的文件名但位置不对，尝试移动
                                if (f.getName().equals("dependency-analysis.md") || f.getName().equals("output.md")) {
                                    try {
                                        java.nio.file.Files.copy(f.toPath(), file.toPath(), 
                                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                        debugInfo("  文件已复制到目标位置: " + file.getAbsolutePath());
                                        f.delete(); // 删除原文件
                                        break;
                                    } catch (Exception e) {
                                        debugError("复制文件失败", e);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // 再次检查文件
            debugInfo("最终检查输出文件: " + absoluteOutputFile);
            debugInfo("文件是否存在: " + file.exists());
            
            if (file.exists()) {
                debugInfo("文件大小: " + file.length() + " bytes");
                debugInfo("文件最后修改时间: " + new java.util.Date(file.lastModified()));
                
                // 读取文件前几行内容
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file))) {
                    debugInfo("文件内容预览:");
                    String line;
                    int lineCount = 0;
                    while ((line = reader.readLine()) != null && lineCount < 10) {
                        debugInfo("  " + (lineCount + 1) + ": " + line);
                        lineCount++;
                    }
                    if (lineCount == 0) {
                        debugInfo("  文件为空！");
                    }
                } catch (Exception e) {
                    debugError("读取文件内容失败", e);
                }
            } else {
                debugInfo("文件仍然不存在！请检查控制台的系统属性配置");
            }
            
            // 分析完成后在UI线程中显示结果
            final boolean fileExists = file.exists();
            shell.getDisplay().asyncExec(() -> {
                MessageDialog.openInformation(shell, "Analysis Completed", 
                    "类分析完成！\n" +
                    "输出文件: " + absoluteOutputFile + "\n" +
                    "文件存在: " + fileExists + "\n\n" +
                    (fileExists ? "请检查项目目录查看分析结果" : "文件生成失败，请查看控制台调试信息"));
            });
            
        } catch (Exception e) {
            debugError("执行分析时出错", e);
            throw new RuntimeException("Analysis execution failed", e);
        }
    }
    
    private void debugJarContents(java.net.URL jarUrl) {
        try {
            debugInfo("=== 检查JAR文件内容: " + jarUrl + " ===");
            
            java.util.jar.JarInputStream jarStream = new java.util.jar.JarInputStream(jarUrl.openStream());
            java.util.jar.JarEntry entry;
            int count = 0;
            
            while ((entry = jarStream.getNextJarEntry()) != null && count < 20) {
                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName().replace('/', '.').replace(".class", "");
                    debugInfo("  找到类: " + className);
                    count++;
                }
            }
            
            if (count >= 20) {
                debugInfo("  ... (还有更多类)");
            }
            
            jarStream.close();
            
        } catch (Exception e) {
            debugError("检查JAR内容时出错", e);
        }
    }
    
    private void setSystemPropertiesFromConfig(PluginConfig config, boolean isClassMode) {
        debugInfo("=== 设置系统属性 ===");
        
        if (isClassMode) {
            setAndDebugProperty("main.class", config.getMainClass());
            setAndDebugProperty("project.root", config.getProjectRoot());
            setAndDebugProperty("project.package.prefixes", config.getProjectPackagePrefixes());
            setAndDebugProperty("method.body.max.depth", String.valueOf(config.getMethodBodyMaxDepth()));
            setAndDebugProperty("keep.only.referenced.methods", String.valueOf(config.isKeepOnlyReferencedMethods()));
            setAndDebugProperty("show.removed.methods", String.valueOf(config.isShowRemovedMethods()));
            setAndDebugProperty("source.directories", config.getSourceDirectories());
            setAndDebugProperty("directory.mode.enabled", "false");
        }
        
        // 使用绝对路径
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
        debugInfo("设置属性: " + key + " = " + value);
    }
    
    private void debugInfo(String message) {
        String debugMsg = "[ClassAnalyzer-DEBUG] " + message;
        
        // 输出到System.out
        System.out.println(debugMsg);
        
        // 也输出到Eclipse控制台
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
            
            // 激活控制台视图 - 使用final变量
            final org.eclipse.ui.console.MessageConsole finalConsole = targetConsole;
            shell.getDisplay().asyncExec(() -> {
                consoleManager.showConsoleView(finalConsole);
            });
            
        } catch (Exception e) {
            // 如果Eclipse控制台失败，至少保证System.out有输出
            System.err.println("Failed to write to Eclipse console: " + e.getMessage());
        }
    }
    
    private void debugError(String message, Exception e) {
        String errorMsg = "[ClassAnalyzer-ERROR] " + message;
        System.err.println(errorMsg);
        if (e != null) {
            e.printStackTrace();
        }

        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        e.printStackTrace(pw);
        String stackTrace = sw.getBuffer().toString();
    
        // 也输出到Eclipse控制台
        debugInfo("ERROR: " + message + (e != null ? " - " + e.getMessage() : "") + "stack trace : " + stackTrace);
    }
}
