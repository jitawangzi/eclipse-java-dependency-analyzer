package io.github.jitawangzi.jdepend.eclipse.actions;

import java.io.File;
import java.util.Properties;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;

import io.github.jitawangzi.jdepend.eclipse.config.PluginConfig;
import io.github.jitawangzi.jdepend.eclipse.dialogs.ConfigurationDialog;
import io.github.jitawangzi.jdepend.eclipse.utils.EclipseProjectUtils;

/**
 * 类分析器动作
 */
public class ClassAnalyzerAction extends AbstractAnalyzerAction {

    private ICompilationUnit selectedUnit;

    @Override
    protected String getLogPrefix() {
        return "ClassAnalyzer";
    }

    @Override
    protected void onSelectionChanged(Object firstElement) {
        selectedUnit = null;
        if (firstElement instanceof IFile) {
            IFile file = (IFile) firstElement;
            if ("java".equals(file.getFileExtension())) {
                selectedUnit = JavaCore.createCompilationUnitFrom(file);
            }
        } else if (firstElement instanceof ICompilationUnit) {
            selectedUnit = (ICompilationUnit) firstElement;
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

            PluginConfig config = new PluginConfig();
            String className = EclipseProjectUtils.getFullyQualifiedClassName(selectedUnit);
            String projectRoot = EclipseProjectUtils.getProjectRootPath(selectedUnit.getResource());
            String packagePrefixes = EclipseProjectUtils.inferPackagePrefixes(selectedUnit);
            String sourceDirectories = EclipseProjectUtils.getSourceDirectories(selectedUnit);

            config.setMainClass(className);
            config.setProjectRoot(projectRoot);
            config.setProjectPackagePrefixes(packagePrefixes);
            config.setSourceDirectories(sourceDirectories);
            config.setOutputFileWithProjectPath(projectRoot, "dependency-analysis.md");

            debugInfo("推断配置: 主类=" + className + ", 项目根=" + projectRoot);

            ConfigurationDialog dialog = new ConfigurationDialog(shell, config, true, className);
            if (dialog.open() == Window.OK) {
                executeAnalysis(dialog.getConfig(), "io.github.jitawangzi.jdepend.ClassAnalyzer");
            }

        } catch (Exception e) {
            debugError("Failed to analyze class: " + e.getMessage(), e);
            MessageDialog.openError(shell, "Error", "Failed to analyze class: " + e.getMessage());
        }
    }

    @Override
    protected String getWorkingDir(PluginConfig config) {
        // 类模式下，工作目录通常设为项目根目录
        return config.getProjectRoot();
    }

    @Override
    protected File[] getOutputSearchDirectories(PluginConfig config) {
        // 类模式下，主要检查项目根目录
        return new File[]{ new File(config.getProjectRoot()) };
    }

    @Override
    protected Properties buildSystemProperties(PluginConfig config) {
        Properties props = new Properties();

        // 类分析特定配置
        putAndDebugProperty(props, "main.class", config.getMainClass());
        putAndDebugProperty(props, "project.root", config.getProjectRoot());
        putAndDebugProperty(props, "project.package.prefixes", config.getProjectPackagePrefixes());
        putAndDebugProperty(props, "method.body.max.depth", String.valueOf(config.getMethodBodyMaxDepth()));
        putAndDebugProperty(props, "keep.only.referenced.methods", String.valueOf(config.isKeepOnlyReferencedMethods()));
        putAndDebugProperty(props, "show.removed.methods", String.valueOf(config.isShowRemovedMethods()));
        putAndDebugProperty(props, "source.directories", config.getSourceDirectories());
        putAndDebugProperty(props, "directory.mode.enabled", "false");

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