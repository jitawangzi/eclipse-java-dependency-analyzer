package io.github.jitawangzi.jdepend.eclipse.actions;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

import io.github.jitawangzi.jdepend.config.AppConfigManager;
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
        Properties systemPropertiesFromConfig = buildSystemPropertiesFromConfig(config);
        AppConfigManager.reload(systemPropertiesFromConfig);
//        io.github.jitawangzi.jdepend.config.AppConfigManager.reload(config.toProperties());
//        apconfigmana
        
        // 在后台线程中执行分析
        Thread analysisThread = new Thread(() -> {
            try {
                executeAnalysisWithDebug(config);
                
            } catch (Throwable  t) {
                String errorMsg = t.getCause() != null ? t.getCause().getMessage() : t.getMessage();
                debugError("分析失败（捕获到严重错误）: " + errorMsg, new Exception(t));  // 包装为 Exception 以打印
                t.printStackTrace();  // 强制打印栈迹
                
                shell.getDisplay().asyncExec(() -> {
                    MessageDialog.openError(shell, "Analysis Failed", 
                        "分析失败: " + errorMsg + "\n可能是内存不足或递归过深。\n详细信息请查看控制台和 Eclipse 日志。");
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
            
            // 动态扫描 lib/ 下所有 JAR
            URL pluginLocation = getClass().getProtectionDomain().getCodeSource().getLocation();
            debugInfo("插件运行位置: " + pluginLocation);

            List<URL> validUrls = new ArrayList<>();

            File locationFile = new File(pluginLocation.toURI());
            debugInfo("locationFile: " + locationFile.getAbsolutePath()
                    + "，isDirectory=" + locationFile.isDirectory());

            if (locationFile.isDirectory()) {
                // 目录模式：pluginLocation = 插件工程根目录 或 bin/classes
                debugInfo("检测到插件以目录形式运行，开始从文件系统扫描 lib 目录 JAR...");

                // 先假定 pluginLocation 就是插件根目录
                File pluginRoot = locationFile;
                debugInfo("初始推断插件根目录为: " + pluginRoot.getAbsolutePath());

                // 如果是 bin/ 或 target/classes/，往上一层
                if ("bin".equalsIgnoreCase(pluginRoot.getName())
                        || "target".equalsIgnoreCase(pluginRoot.getName())
                        || "classes".equalsIgnoreCase(pluginRoot.getName())) {
                    pluginRoot = pluginRoot.getParentFile();
                    debugInfo("根据目录名修正插件根目录为: " + pluginRoot.getAbsolutePath());
                }

                File libDir = new File(pluginRoot, "lib");
                debugInfo("最终推断的 lib 目录为: " + libDir.getAbsolutePath());

                if (libDir.isDirectory()) {
                    File[] jars = libDir.listFiles((dir, name) -> name.endsWith(".jar"));
                    if (jars != null) {
                        for (File jar : jars) {
                            URL jarUrl = jar.toURI().toURL();
                            validUrls.add(jarUrl);
                            debugInfo("找到lib目录下JAR: " + jar.getName() + " -> " + jarUrl);
                        }
                    }
                } else {
                    debugInfo("lib 目录不存在: " + libDir.getAbsolutePath());
                }

            } else {
                // JAR 模式：和你原来的逻辑一样
                debugInfo("检测到插件以JAR形式运行，开始从JAR内部扫描 lib 目录 JAR...");

                try (InputStream jarStream = pluginLocation.openStream();
                     ZipInputStream zipInput = new ZipInputStream(jarStream)) {
                    ZipEntry entry;
                    while ((entry = zipInput.getNextEntry()) != null) {
                        String name = entry.getName();
                        if (name.startsWith("lib/") && name.endsWith(".jar") && !entry.isDirectory()) {
                            URL innerJarUrl = new URL("jar:" + pluginLocation + "!/" + name);
                            validUrls.add(innerJarUrl);
                            debugInfo("找到嵌入 JAR: " + name + " -> " + innerJarUrl);
                        }
                    }
                }
            }

            if (validUrls.isEmpty()) {
                throw new Exception("没有找到任何必需的JAR文件！请确认：\n"
                        + "1) 调试模式下，插件工程根目录下存在 lib/xxx.jar\n"
                        + "2) 打包模式下，插件JAR内部有 lib/xxx.jar");
            }

            debugInfo("共找到 " + validUrls.size() + " 个JAR文件");

            // 构建 URLClassLoader
            URLClassLoader customClassLoader =
                    new URLClassLoader(validUrls.toArray(new URL[0]), getClass().getClassLoader());
            Thread.currentThread().setContextClassLoader(customClassLoader);
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
            String projectRoot = System.getProperty("project.root");  // 从系统属性获取
            String setDir = (projectRoot != null && !projectRoot.isEmpty()) ? projectRoot : config.getDirectoryPath();
            System.setProperty("user.dir", setDir);
            debugInfo("设置工作目录为: " + setDir);
            
            try {
                // 调用main方法
                debugInfo("开始执行分析...");
                debugInfo("调用 DirectoryAnalyzer.main()，目录文件数约: " + new java.io.File(config.getDirectoryPath()).listFiles().length);
                mainMethod.invoke(null, (Object) new String[0]);
                debugInfo("分析执行完成");
                debugInfo("main() 返回，无异常");
            }catch (Exception e) {
				debugError("调用分析器main方法时出错", e);
				throw e;
			}finally {
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

		// 如果目标位置没有文件，检查工作目录或项目根目录下是否有匹配的 .md 文件
		if (!file.exists()) {
			debugInfo("目标位置没有文件，检查工作目录和项目根目录...");

			// 检查工作目录 (user.dir 在分析时设置为 parentDir)
			java.io.File workDir = new java.io.File(config.getDirectoryPath()).getParentFile();
			java.io.File rootDir = new java.io.File(EclipseProjectUtils.getProjectRootPath(selectedResource)); // 项目根

			java.io.File[] dirsToCheck = { workDir, rootDir };
			String[] possibleFileNames = { "directory-analysis.md", "output.md", "dependency-analysis.md" }; // 可能的文件名

			for (java.io.File dir : dirsToCheck) {
				if (dir != null && dir.exists()) {
					java.io.File[] dirFiles = dir.listFiles();
					if (dirFiles != null) {
						for (java.io.File f : dirFiles) {
							if (f.isFile() && f.getName().endsWith(".md")) {
								for (String possibleName : possibleFileNames) {
									if (f.getName().equals(possibleName)) {
										try {
											java.nio.file.Files.copy(f.toPath(), file.toPath(),
													java.nio.file.StandardCopyOption.REPLACE_EXISTING);
											debugInfo("文件已从 " + f.getAbsolutePath() + " 复制到目标位置: " + file.getAbsolutePath());
											f.delete(); // 删除原文件
											break; // 找到一个后停止
										} catch (Exception e) {
											debugError("复制文件失败", e);
										}
									}
								}
							}
						}
					}
				}
			}
		}

		// 最终结果检查（原有代码）
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
				MessageDialog.openInformation(shell, "Analysis Completed", "目录分析完成！\n\n" + "输出文件: " + file.getName() + "\n" + "文件大小: "
						+ (fileSize / 1024) + " KB\n" + "位置: " + file.getParent());
			} else {
				MessageDialog.openWarning(shell, "Analysis Completed", "分析已执行完成，但输出文件未生成。\n\n" + "请检查控制台输出了解详细信息。");
			}
		});
	}
    
    private void setSystemPropertiesFromConfig(PluginConfig config) {
        debugInfo("设置系统属性配置...");
        
        // 动态推断是否需要 project.root
        String extensions = config.getDirectoryAllowedExtensions();  // 从 config 获取扩展名
        boolean isJavaAnalysis = extensions != null && extensions.contains("java");  // 如果包含 java，需要项目根
        
        String projectRoot;
        if (isJavaAnalysis) {
            // 需要 Java 分析：从资源推断项目根（如类模式）
            projectRoot = EclipseProjectUtils.getProjectRootPath(selectedResource);
            if (projectRoot == null || projectRoot.trim().isEmpty()) {
                projectRoot = config.getDirectoryPath();  // fallback 到目录路径
                debugInfo("警告: 未找到项目根，使用目录路径作为 fallback: " + projectRoot);
            }
        } else {
            // 非 Java 分析：不需要根目录，设为空或目录本身
            projectRoot = "";  // 或 config.getDirectoryPath() 如果需要
            debugInfo("非 Java 分析模式，不设置 project.root");
        }
        setAndDebugProperty("project.root", projectRoot);  // 设置属性
        
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
    /**
     * 根据 PluginConfig 生成一份 Properties，而不是直接写 System properties。
     * @return 构造好的 Properties
     */
    private Properties buildSystemPropertiesFromConfig(PluginConfig config) {
        debugInfo("设置系统属性配置...");

        Properties props = new Properties();

        // 动态推断是否需要 project.root
        String extensions = config.getDirectoryAllowedExtensions();  // 从 config 获取扩展名
        boolean isJavaAnalysis = extensions != null && extensions.contains(".java");  // 如果包含 .java，需要项目根

        String projectRoot;
        if (isJavaAnalysis) {
            // 需要 Java 分析：从资源推断项目根（如类模式）
            projectRoot = EclipseProjectUtils.getProjectRootPath(selectedResource);
            if (projectRoot == null || projectRoot.trim().isEmpty()) {
                projectRoot = config.getDirectoryPath();  // fallback 到目录路径
                debugInfo("警告: 未找到项目根，使用目录路径作为 fallback: " + projectRoot);
            }
        } else {
            // 非 Java 分析：不需要根目录，设为空或目录本身
            projectRoot = "";  // 或 config.getDirectoryPath() 如果需要
            debugInfo("非 Java 分析模式，不设置 project.root");
        }
        putAndDebugProperty(props, "project.root", projectRoot);

        // 目录分析配置
        putAndDebugProperty(props, "directory.mode.enabled", "true");
        putAndDebugProperty(props, "directory.path", config.getDirectoryPath());
        putAndDebugProperty(props, "simplify.methods", String.valueOf(config.isSimplifyMethods()));
        putAndDebugProperty(props, "directory.include.files", config.getDirectoryIncludeFiles());
        putAndDebugProperty(props, "directory.exclude.files", config.getDirectoryExcludeFiles());
        putAndDebugProperty(props, "directory.include.folders", config.getDirectoryIncludeFolders());
        putAndDebugProperty(props, "directory.exclude.folders", config.getDirectoryExcludeFolders());
        putAndDebugProperty(props, "directory.allowed.extensions", config.getDirectoryAllowedExtensions());

        // 通用配置
        putAndDebugProperty(props, "output.file", config.getAbsoluteOutputFile());
        putAndDebugProperty(props, "max.depth", String.valueOf(config.getMaxDepth()));
        putAndDebugProperty(props, "excluded.packages", config.getExcludedPackages());
        putAndDebugProperty(props, "method.exceptions", config.getMethodExceptions());
        putAndDebugProperty(props, "content.size.threshold", String.valueOf(config.getContentSizeThreshold()));
        putAndDebugProperty(props, "omit.bean.methods", String.valueOf(config.isOmitBeanMethods()));
        putAndDebugProperty(props, "show.omitted.accessors", String.valueOf(config.isShowOmittedAccessors()));
        putAndDebugProperty(props, "import.skip.enabled", String.valueOf(config.isImportSkipEnabled()));
        putAndDebugProperty(props, "import.skip.prefixes", config.getImportSkipPrefixes());
        putAndDebugProperty(props, "import.keep.prefixes", config.getImportKeepPrefixes());
        putAndDebugProperty(props, "show.error.stacktrace", String.valueOf(config.isShowErrorStacktrace()));

        return props;
    }

    /**
     * 往 Properties 里放值并打印日志，不再直接调用 System.setProperty。
     */
    private void putAndDebugProperty(Properties props, String key, String value) {
        if (value != null) {
            props.setProperty(key, value);
        } else {
            // 如果你希望跳过 null，可以只打印日志；也可以 props.setProperty(key, "");
            debugInfo("  " + key + " = <null> (跳过设置)");
            return;
        }
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
