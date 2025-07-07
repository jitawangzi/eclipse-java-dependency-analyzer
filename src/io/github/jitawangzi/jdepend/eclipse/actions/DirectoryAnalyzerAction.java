package io.github.jitawangzi.jdepend.eclipse.actions;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
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
 * 目录分析器动作
 */
public class DirectoryAnalyzerAction implements IObjectActionDelegate {
    
    private Shell shell;
    private IResource selectedResource;
    
    @Override
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
        shell = targetPart.getSite().getShell();
    }
    
    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        selectedResource = null;
        if (selection instanceof IStructuredSelection) {
            IStructuredSelection structuredSelection = (IStructuredSelection) selection;
            Object firstElement = structuredSelection.getFirstElement();
            
            if (firstElement instanceof IFolder || firstElement instanceof IProject) {
                selectedResource = (IResource) firstElement;
            }
        }
    }
    
    @Override
    public void run(IAction action) {
        if (selectedResource == null) {
            MessageDialog.openError(shell, "Error", "Please select a folder or project.");
            return;
        }
        
        try {
            debugInfo("=== DirectoryAnalyzerAction 开始执行 ===");
            
            // 准备默认配置
            PluginConfig config = new PluginConfig();
            
            // 从选中的资源推断配置
            String directoryPath = EclipseProjectUtils.getResourcePath(selectedResource);
            config.setDirectoryPath(directoryPath);
            
            // 设置输出文件到目录的父目录或项目根目录
            java.io.File dirFile = new java.io.File(directoryPath);
            java.io.File parentDir = dirFile.getParentFile();
            if (parentDir != null) {
                config.setOutputFileWithProjectPath(parentDir.getAbsolutePath(), "directory-analysis.md");
            } else {
                config.setOutputFileWithProjectPath(directoryPath, "directory-analysis.md");
            }
            
            debugInfo("推断配置: 目录路径=" + directoryPath + ", 输出文件=" + config.getAbsoluteOutputFile());
            
            // 显示配置对话框
            ConfigurationDialog dialog = new ConfigurationDialog(shell, config, false, directoryPath);
            if (dialog.open() == Window.OK) {
                // 获取配置并执行分析
                PluginConfig finalConfig = dialog.getConfig();
                executeDirectoryAnalysis(finalConfig);
            }
            
        } catch (Exception e) {
            String errorMsg = "Failed to analyze directory: " + e.getMessage();
            debugError(errorMsg, e);
            MessageDialog.openError(shell, "Error", errorMsg);
        }
    }
    
    private void executeDirectoryAnalysis(PluginConfig config) {
        debugInfo("开始执行目录分析，目录: " + config.getDirectoryPath());
        
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
        
        analysisThread.setName("DirectoryAnalysis-Thread");
        analysisThread.start();
    }
    
    private void executeAnalysisWithDebug(PluginConfig config) {
        try {
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
                if (jarUrl != null) {
                    validUrls.add(jarUrl);
                    debugInfo("找到JAR: " + jarPath);
                } else {
                    debugInfo("警告: 找不到JAR文件: " + jarPath);
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
            
            // 尝试加载DirectoryAnalyzer类
            String targetClassName = "io.github.jitawangzi.jdepend.DirectoryAnalyzer";
            Class<?> directoryAnalyzerClass = customClassLoader.loadClass(targetClassName);
            debugInfo("成功加载分析器类: " + directoryAnalyzerClass.getName());
            
            // 获取main方法
            java.lang.reflect.Method mainMethod = directoryAnalyzerClass.getMethod("main", String[].class);
            
            // 保存和设置工作目录
            String originalUserDir = System.getProperty("user.dir");
            String directoryPath = config.getDirectoryPath();
            java.io.File dirFile = new java.io.File(directoryPath);
            java.io.File parentDir = dirFile.getParentFile();
            if (parentDir != null && parentDir.exists()) {
                System.setProperty("user.dir", parentDir.getAbsolutePath());
                debugInfo("设置工作目录为: " + parentDir.getAbsolutePath());
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
                    "目录分析完成！\n\n" +
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
        
        // 目录分析配置
        setAndDebugProperty("directory.mode.enabled", "true");
        setAndDebugProperty("directory.path", config.getDirectoryPath());
        setAndDebugProperty("simplify.methods", String.valueOf(config.isSimplifyMethods()));
        setAndDebugProperty("directory.include.files", config.getDirectoryIncludeFiles());
        setAndDebugProperty("directory.exclude.files", config.getDirectoryExcludeFiles());
        setAndDebugProperty("directory.include.folders", config.getDirectoryIncludeFolders());
        setAndDebugProperty("directory.exclude.folders", config.getDirectoryExcludeFolders());
        setAndDebugProperty("directory.allowed.extensions", config.getDirectoryAllowedExtensions());
        
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
        String debugMsg = "[DirectoryAnalyzer] " + message;
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
        String errorMsg = "[DirectoryAnalyzer-ERROR] " + message;
        System.err.println(errorMsg);
        if (e != null) {
            e.printStackTrace();
        }
        debugInfo("ERROR: " + message + (e != null ? " - " + e.getMessage() : ""));
    }
}
