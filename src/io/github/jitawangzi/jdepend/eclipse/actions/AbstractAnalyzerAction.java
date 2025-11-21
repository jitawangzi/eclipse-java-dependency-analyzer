package io.github.jitawangzi.jdepend.eclipse.actions;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

import io.github.jitawangzi.jdepend.config.AppConfigManager;
import io.github.jitawangzi.jdepend.eclipse.config.PluginConfig;

/**
 * 分析器动作的抽象基类
 * 封装了通用的类加载、线程执行、日志记录和文件处理逻辑
 */
public abstract class AbstractAnalyzerAction implements IObjectActionDelegate {

    protected Shell shell;
    protected Object selectedElement;

    @Override
    public void setActivePart(IAction action, IWorkbenchPart targetPart) {
        shell = targetPart.getSite().getShell();
    }

    @Override
    public void selectionChanged(IAction action, ISelection selection) {
        selectedElement = null;
        if (selection instanceof IStructuredSelection) {
            selectedElement = ((IStructuredSelection) selection).getFirstElement();
            onSelectionChanged(selectedElement);
        }
    }

    /**
     * 子类处理具体的选择逻辑
     */
    protected abstract void onSelectionChanged(Object firstElement);

    /**
     * 构建特定于模式的系统属性
     */
    protected abstract Properties buildSystemProperties(PluginConfig config);

    /**
     * 获取分析运行时的工作目录 (user.dir)
     */
    protected abstract String getWorkingDir(PluginConfig config);

    /**
     * 获取用于查找生成结果的备选目录列表
     */
    protected abstract File[] getOutputSearchDirectories(PluginConfig config);

    /**
     * 获取日志前缀
     */
    protected abstract String getLogPrefix();

    /**
     * 执行分析的核心入口
     * @param config 配置对象
     * @param analyzerClassName 要反射调用的主类全限定名
     */
    protected void executeAnalysis(PluginConfig config, String analyzerClassName) {
        debugInfo("开始执行分析，目标类: " + analyzerClassName);

        // 1. 设置系统属性并重载配置
        Properties systemProperties = buildSystemProperties(config);
        AppConfigManager.reload(systemProperties);

        // 2. 在后台线程中执行
        Thread analysisThread = new Thread(() -> {
            try {
                executeAnalysisLogic(config, analyzerClassName);
            } catch (Throwable t) {
                String errorMsg = t.getCause() != null ? t.getCause().getMessage() : t.getMessage();
                debugError("分析失败（捕获到严重错误）: " + errorMsg, new Exception(t));
                t.printStackTrace();

                shell.getDisplay().asyncExec(() -> {
                    MessageDialog.openError(shell, "Analysis Failed",
                            "分析失败: " + errorMsg + "\n可能是内存不足或递归过深。\n详细信息请查看控制台和 Eclipse 日志。");
                });
            }
        });

        analysisThread.setName(getLogPrefix() + "-Thread");
        analysisThread.start();
    }

    private void executeAnalysisLogic(PluginConfig config, String targetClassName) throws Exception {
        // 1. 准备类加载器
        ClassLoader pluginClassLoader = this.getClass().getClassLoader();
        debugInfo("插件类加载器: " + pluginClassLoader.getClass().getName());

        List<URL> validUrls = scanForLibJars();
        if (validUrls.isEmpty()) {
            throw new Exception("没有找到任何必需的JAR文件！请确认插件 lib 目录或 JAR 包结构。");
        }
        debugInfo("共找到 " + validUrls.size() + " 个JAR文件");

        // 2. 构建 URLClassLoader
        URLClassLoader customClassLoader = new URLClassLoader(validUrls.toArray(new URL[0]), pluginClassLoader);
        Thread.currentThread().setContextClassLoader(customClassLoader);

        // 3. 加载分析器类
        Class<?> analyzerClass = customClassLoader.loadClass(targetClassName);
        debugInfo("成功加载分析器类: " + analyzerClass.getName());
        java.lang.reflect.Method mainMethod = analyzerClass.getMethod("main", String[].class);

        // 4. 设置工作目录
        String originalUserDir = System.getProperty("user.dir");
        String workingDir = getWorkingDir(config);
        if (workingDir != null && !workingDir.isEmpty()) {
            System.setProperty("user.dir", workingDir);
            debugInfo("设置工作目录为: " + workingDir);
        }

        try {
            // 5. 调用 main 方法
            debugInfo("开始执行分析...");
            mainMethod.invoke(null, (Object) new String[0]);
            debugInfo("分析执行完成");
        } catch (Exception e) {
            debugError("调用分析器main方法时出错", e);
            throw e;
        } finally {
            // 恢复原始工作目录
            if (originalUserDir != null) {
                System.setProperty("user.dir", originalUserDir);
            }
            debugInfo("恢复工作目录");
        }

        // 6. 检查结果
        checkAndProcessOutputFile(config);
    }

    /**
     * 扫描插件环境下的 lib JAR 包 (支持目录模式和JAR模式)
     */
    private List<URL> scanForLibJars() throws Exception {
        List<URL> validUrls = new ArrayList<>();
        URL pluginLocation = getClass().getProtectionDomain().getCodeSource().getLocation();
        debugInfo("插件运行位置: " + pluginLocation);

        File locationFile = new File(pluginLocation.toURI());

        if (locationFile.isDirectory()) {
            // 目录模式
            debugInfo("检测到插件以目录形式运行，扫描文件系统...");
            File pluginRoot = locationFile;
            if ("bin".equalsIgnoreCase(pluginRoot.getName())
                    || "target".equalsIgnoreCase(pluginRoot.getName())
                    || "classes".equalsIgnoreCase(pluginRoot.getName())) {
                pluginRoot = pluginRoot.getParentFile();
            }
            File libDir = new File(pluginRoot, "lib");
            if (libDir.isDirectory()) {
                File[] jars = libDir.listFiles((dir, name) -> name.endsWith(".jar"));
                if (jars != null) {
                    for (File jar : jars) {
                        validUrls.add(jar.toURI().toURL());
                    }
                }
            }
        } else {
            // JAR 模式
            debugInfo("检测到插件以JAR形式运行，扫描内部 lib...");
            try (InputStream jarStream = pluginLocation.openStream();
                 ZipInputStream zipInput = new ZipInputStream(jarStream)) {
                ZipEntry entry;
                while ((entry = zipInput.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (name.startsWith("lib/") && name.endsWith(".jar") && !entry.isDirectory()) {
                        URL innerJarUrl = new URL("jar:" + pluginLocation + "!/" + name);
                        validUrls.add(innerJarUrl);
                    }
                }
            }
        }
        return validUrls;
    }

    private void checkAndProcessOutputFile(PluginConfig config) {
        String absoluteOutputFile = config.getAbsoluteOutputFile();
        File targetFile = new File(absoluteOutputFile);
        debugInfo("检查输出文件: " + absoluteOutputFile);

        // 如果目标位置没有文件，检查备选目录
        if (!targetFile.exists()) {
            debugInfo("目标位置没有文件，检查备选目录...");
            File[] dirsToCheck = getOutputSearchDirectories(config);
            String[] possibleFileNames = { "directory-analysis.md", "dependency-analysis.md", "output.md" };

            outerLoop:
            for (File dir : dirsToCheck) {
                if (dir != null && dir.exists()) {
                    for (String possibleName : possibleFileNames) {
                        File candidate = new File(dir, possibleName);
                        if (candidate.exists() && candidate.isFile()) {
                            try {
                                Files.copy(candidate.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                debugInfo("文件已从 " + candidate.getAbsolutePath() + " 复制到目标位置");
                                candidate.delete();
                                break outerLoop;
                            } catch (Exception e) {
                                debugError("复制文件失败", e);
                            }
                        }
                    }
                }
            }
        }

        final boolean fileExists = targetFile.exists();
        final long fileSize = fileExists ? targetFile.length() : 0;

        shell.getDisplay().asyncExec(() -> {
            if (fileExists) {
                MessageDialog.openInformation(shell, "Analysis Completed",
                        "分析完成！\n\n输出文件: " + targetFile.getName() + "\n大小: " + (fileSize / 1024) + " KB\n位置: " + targetFile.getParent());
            } else {
                MessageDialog.openWarning(shell, "Analysis Completed", "分析已执行完成，但输出文件未生成。\n请检查控制台输出。");
            }
        });
    }

    protected void putAndDebugProperty(Properties props, String key, String value) {
        if (value != null) {
            props.setProperty(key, value);
            debugInfo("  " + key + " = " + value);
        } else {
            debugInfo("  " + key + " = <null> (跳过设置)");
        }
    }

    protected void debugInfo(String message) {
        String debugMsg = "[" + getLogPrefix() + "] " + message;
        System.out.println(debugMsg);
        logToEclipseConsole(debugMsg);
    }

    protected void debugError(String message, Exception e) {
        String errorMsg = "[" + getLogPrefix() + "-ERROR] " + message;
        System.err.println(errorMsg);
        if (e != null) e.printStackTrace();
        logToEclipseConsole(errorMsg + (e != null ? " - " + e.getMessage() : ""));
    }

    private void logToEclipseConsole(String msg) {
        try {
            IConsoleManager consoleManager = ConsolePlugin.getDefault().getConsoleManager();
            MessageConsole targetConsole = null;
            for (IConsole existingConsole : consoleManager.getConsoles()) {
                if ("Java Dependency Analyzer".equals(existingConsole.getName())) {
                    targetConsole = (MessageConsole) existingConsole;
                    break;
                }
            }
            if (targetConsole == null) {
                targetConsole = new MessageConsole("Java Dependency Analyzer", null);
                consoleManager.addConsoles(new IConsole[]{targetConsole});
            }
            MessageConsoleStream stream = targetConsole.newMessageStream();
            stream.println(msg);
            
            // 可选：自动显示控制台
            // final MessageConsole finalConsole = targetConsole;
            // shell.getDisplay().asyncExec(() -> consoleManager.showConsoleView(finalConsole));
        } catch (Exception e) {
            System.err.println("Failed to write to Eclipse console: " + e.getMessage());
        }
    }
}