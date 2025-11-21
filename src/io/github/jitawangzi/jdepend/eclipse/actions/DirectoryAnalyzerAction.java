package io.github.jitawangzi.jdepend.eclipse.actions;

import java.io.File;
import java.util.Properties;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;

import io.github.jitawangzi.jdepend.eclipse.config.PluginConfig;
import io.github.jitawangzi.jdepend.eclipse.dialogs.ConfigurationDialog;
import io.github.jitawangzi.jdepend.eclipse.utils.EclipseProjectUtils;

/**
 * 目录分析器动作
 */
public class DirectoryAnalyzerAction extends AbstractAnalyzerAction {

    private IResource selectedResource;

    @Override
    protected String getLogPrefix() {
        return "DirectoryAnalyzer";
    }

    @Override
    protected void onSelectionChanged(Object firstElement) {
        selectedResource = null;
        if (firstElement instanceof IFolder || firstElement instanceof IProject) {
            selectedResource = (IResource) firstElement;
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

            PluginConfig config = new PluginConfig();
            String directoryPath = EclipseProjectUtils.getResourcePath(selectedResource);
            config.setDirectoryPath(directoryPath);

            // 设置输出文件
            File dirFile = new File(directoryPath);
            File parentDir = dirFile.getParentFile();
            if (parentDir != null) {
                config.setOutputFileWithProjectPath(parentDir.getAbsolutePath(), "directory-analysis.md");
            } else {
                config.setOutputFileWithProjectPath(directoryPath, "directory-analysis.md");
            }

            debugInfo("推断配置: 目录=" + directoryPath + ", 输出=" + config.getAbsoluteOutputFile());

            ConfigurationDialog dialog = new ConfigurationDialog(shell, config, false, directoryPath);
            if (dialog.open() == Window.OK) {
                executeAnalysis(dialog.getConfig(), "io.github.jitawangzi.jdepend.DirectoryAnalyzer");
            }

        } catch (Exception e) {
            debugError("Failed to analyze directory: " + e.getMessage(), e);
            MessageDialog.openError(shell, "Error", "Failed to analyze directory: " + e.getMessage());
        }
    }

    @Override
    protected String getWorkingDir(PluginConfig config) {
        // 目录模式下，优先使用 project.root，如果未设定则使用目录路径
        String projectRoot = System.getProperty("project.root");
        if (projectRoot != null && !projectRoot.isEmpty()) {
            return projectRoot;
        }
        return config.getDirectoryPath();
    }

    @Override
    protected File[] getOutputSearchDirectories(PluginConfig config) {
        // 检查工作目录(目录的父级) 和 项目根目录
        File workDir = new File(config.getDirectoryPath()).getParentFile();
        File rootDir = new File(EclipseProjectUtils.getProjectRootPath(selectedResource));
        return new File[]{workDir, rootDir};
    }

    @Override
    protected Properties buildSystemProperties(PluginConfig config) {
        Properties props = new Properties();

        // 动态推断是否需要 project.root (如果包含 .java 分析)
        String extensions = config.getDirectoryAllowedExtensions();
        boolean isJavaAnalysis = extensions != null && extensions.contains(".java");
        String projectRoot = "";
        
        if (isJavaAnalysis) {
            projectRoot = EclipseProjectUtils.getProjectRootPath(selectedResource);
            if (projectRoot == null || projectRoot.trim().isEmpty()) {
                projectRoot = config.getDirectoryPath();
            }
        }
        putAndDebugProperty(props, "project.root", projectRoot);

        // 目录特定配置
        putAndDebugProperty(props, "directory.mode.enabled", "true");
        putAndDebugProperty(props, "directory.path", config.getDirectoryPath());
        putAndDebugProperty(props, "simplify.methods", String.valueOf(config.isSimplifyMethods()));
        putAndDebugProperty(props, "directory.include.files", config.getDirectoryIncludeFiles());
        putAndDebugProperty(props, "directory.exclude.files", config.getDirectoryExcludeFiles());
        putAndDebugProperty(props, "directory.include.folders", config.getDirectoryIncludeFolders());
        putAndDebugProperty(props, "directory.exclude.folders", config.getDirectoryExcludeFolders());
        putAndDebugProperty(props, "directory.allowed.extensions", config.getDirectoryAllowedExtensions());

        // 通用配置
        addCommonProperties(props, config);

        return props;
    }
    
    private void addCommonProperties(Properties props, PluginConfig config) {
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
    }
}