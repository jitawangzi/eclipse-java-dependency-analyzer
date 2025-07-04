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
 * 类分析器动作 - 带调试信息版本
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
            
            debugInfo("配置信息:\n" +
                "主类: " + className + "\n" +
                "项目根路径: " + projectRoot + "\n" +
                "包前缀: " + packagePrefixes + "\n" +
                "源码目录: " + sourceDirectories);
            
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
            
            debugInfo("=== 准备调用main方法 ===");
            debugInfo("输出文件: " + config.getOutputFile());
            
            // 显示即将开始分析的信息
            shell.getDisplay().asyncExec(() -> {
                MessageDialog.openInformation(shell, "Analysis Started", 
                    "开始执行类分析...\n" +
                    "主类: " + config.getMainClass() + "\n" +
                    "输出文件: " + config.getOutputFile() + "\n\n" +
                    "详细调试信息请查看控制台");
            });
            
            // 调用main方法
            mainMethod.invoke(null, (Object) new String[0]);
            
            debugInfo("=== main方法调用完成 ===");
            
            // 分析完成后在UI线程中显示结果
            shell.getDisplay().asyncExec(() -> {
                MessageDialog.openInformation(shell, "Analysis Completed", 
                    "类分析完成！\n" +
                    "输出文件: " + config.getOutputFile() + "\n\n" +
                    "请检查输出文件查看结果");
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
        }
        
        setAndDebugProperty("output.file", config.getOutputFile());
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
        System.out.println("[ClassAnalyzer-DEBUG] " + message);
    }
    
    private void debugError(String message, Exception e) {
        System.err.println("[ClassAnalyzer-ERROR] " + message);
        if (e != null) {
            e.printStackTrace();
        }
    }
}
