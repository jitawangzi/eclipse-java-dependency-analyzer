package io.github.jitawangzi.jdepend.eclipse.dialogs;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import io.github.jitawangzi.jdepend.eclipse.config.PluginConfig;

/**
 * 配置对话框
 */
public class ConfigurationDialog extends Dialog {
    
    private PluginConfig config;
    private boolean isClassMode;
    private String presetValue;
    
    // 控件
    private Text mainClassText;
    private Text projectRootText;
    private Text projectPackagePrefixesText;
    private Text methodBodyMaxDepthText;
    private Button keepOnlyReferencedMethodsCheck;
    private Button showRemovedMethodsCheck;
    private Text sourceDirectoriesText;
    
    private Text outputFileText;
    private Text maxDepthText;
    private Text excludedPackagesText;
    private Text methodExceptionsText;
    private Text contentSizeThresholdText;
    private Button omitBeanMethodsCheck;
    private Button showOmittedAccessorsCheck;
    private Button importSkipEnabledCheck;
    private Text importSkipPrefixesText;
    private Text importKeepPrefixesText;
    private Button showErrorStacktraceCheck;
    
    private Text directoryPathText;
    private Button simplifyMethodsCheck;
    private Text directoryIncludeFilesText;
    private Text directoryExcludeFilesText;
    private Text directoryIncludeFoldersText;
    private Text directoryExcludeFoldersText;
    private Text directoryAllowedExtensionsText;
    
    // 新添加：Checkbox 用于选择是否作为 Java 工程分析
    private Button isJavaProjectCheck;
    private Button openOutputDirectoryCheck;
    
    public ConfigurationDialog(Shell parentShell, PluginConfig config, boolean isClassMode, String presetValue) {
        super(parentShell);
        this.config = config;
        this.isClassMode = isClassMode;
        this.presetValue = presetValue;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }
    
    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText(isClassMode ? "Class Analyzer Configuration" : "Directory Analyzer Configuration");
        newShell.setSize(600, 700);
    }
    
    @Override
    protected Control createDialogArea(Composite parent) {
        Composite container = (Composite) super.createDialogArea(parent);
        container.setLayout(new GridLayout(1, false));
        
        // 创建标签页
        TabFolder tabFolder = new TabFolder(container, SWT.NONE);
        tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        
        if (isClassMode) {
            createClassAnalysisTab(tabFolder);
        }
        createCommonTab(tabFolder);
        if (!isClassMode) {
            createDirectoryAnalysisTab(tabFolder);
        }
        
        // 预设值
        if (presetValue != null && !presetValue.isEmpty()) {
            if (isClassMode) {
                config.setMainClass(presetValue);
                if (mainClassText != null) {
                    mainClassText.setText(presetValue);
                }
            } else {
                config.setDirectoryPath(presetValue);
                if (directoryPathText != null) {
                    directoryPathText.setText(presetValue);
                }
            }
        }
        
        return container;
    }
    
    private void createClassAnalysisTab(TabFolder tabFolder) {
        TabItem classTab = new TabItem(tabFolder, SWT.NONE);
        classTab.setText("Class Analysis");
        
        Composite classComposite = new Composite(tabFolder, SWT.NONE);
        classComposite.setLayout(new GridLayout(2, false));
        classTab.setControl(classComposite);
        
        // 主类
        new Label(classComposite, SWT.NONE).setText("Main Class:");
        mainClassText = new Text(classComposite, SWT.BORDER);
        mainClassText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        mainClassText.setText(config.getMainClass());
        
        // 项目根目录
        new Label(classComposite, SWT.NONE).setText("Project Root:");
        projectRootText = new Text(classComposite, SWT.BORDER);
        projectRootText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        projectRootText.setText(config.getProjectRoot());
        
        // 项目包前缀
        new Label(classComposite, SWT.NONE).setText("Package Prefixes:");
        projectPackagePrefixesText = new Text(classComposite, SWT.BORDER);
        projectPackagePrefixesText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        projectPackagePrefixesText.setText(config.getProjectPackagePrefixes());
        
        // 方法体最大深度
        new Label(classComposite, SWT.NONE).setText("Method Body Max Depth:");
        methodBodyMaxDepthText = new Text(classComposite, SWT.BORDER);
        methodBodyMaxDepthText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        methodBodyMaxDepthText.setText(String.valueOf(config.getMethodBodyMaxDepth()));
        
        // 只保留被引用的方法
        new Label(classComposite, SWT.NONE).setText("Keep Only Referenced Methods:");
        keepOnlyReferencedMethodsCheck = new Button(classComposite, SWT.CHECK);
        keepOnlyReferencedMethodsCheck.setSelection(config.isKeepOnlyReferencedMethods());
        
        // 显示移除的方法
        new Label(classComposite, SWT.NONE).setText("Show Removed Methods:");
        showRemovedMethodsCheck = new Button(classComposite, SWT.CHECK);
        showRemovedMethodsCheck.setSelection(config.isShowRemovedMethods());
        
        // 源码目录
        new Label(classComposite, SWT.NONE).setText("Source Directories:");
        sourceDirectoriesText = new Text(classComposite, SWT.BORDER);
        sourceDirectoriesText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        sourceDirectoriesText.setText(config.getSourceDirectories());
    }
    
    private void createCommonTab(TabFolder tabFolder) {
        TabItem commonTab = new TabItem(tabFolder, SWT.NONE);
        commonTab.setText("Common");
        
        Composite commonComposite = new Composite(tabFolder, SWT.NONE);
        commonComposite.setLayout(new GridLayout(2, false));
        commonTab.setControl(commonComposite);
        
        // 输出文件
        new Label(commonComposite, SWT.NONE).setText("Output File:");
        outputFileText = new Text(commonComposite, SWT.BORDER);
        outputFileText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        outputFileText.setText(config.getOutputFile());
        
        // 最大深度
        new Label(commonComposite, SWT.NONE).setText("Max Depth:");
        maxDepthText = new Text(commonComposite, SWT.BORDER);
        maxDepthText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        maxDepthText.setText(String.valueOf(config.getMaxDepth()));
        
        // 排除的包
        new Label(commonComposite, SWT.NONE).setText("Excluded Packages:");
        excludedPackagesText = new Text(commonComposite, SWT.BORDER);
        excludedPackagesText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        excludedPackagesText.setText(config.getExcludedPackages());
        
        // 方法例外
        new Label(commonComposite, SWT.NONE).setText("Method Exceptions:");
        methodExceptionsText = new Text(commonComposite, SWT.BORDER);
        methodExceptionsText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        methodExceptionsText.setText(config.getMethodExceptions());
        
        // 内容大小阈值
        new Label(commonComposite, SWT.NONE).setText("Content Size Threshold:");
        contentSizeThresholdText = new Text(commonComposite, SWT.BORDER);
        contentSizeThresholdText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        contentSizeThresholdText.setText(String.valueOf(config.getContentSizeThreshold()));
        
        // 省略Bean方法
        new Label(commonComposite, SWT.NONE).setText("Omit Bean Methods:");
        omitBeanMethodsCheck = new Button(commonComposite, SWT.CHECK);
        omitBeanMethodsCheck.setSelection(config.isOmitBeanMethods());
        
        // 显示省略的访问器
        new Label(commonComposite, SWT.NONE).setText("Show Omitted Accessors:");
        showOmittedAccessorsCheck = new Button(commonComposite, SWT.CHECK);
        showOmittedAccessorsCheck.setSelection(config.isShowOmittedAccessors());
        
        // 启用导入跳过
        new Label(commonComposite, SWT.NONE).setText("Import Skip Enabled:");
        importSkipEnabledCheck = new Button(commonComposite, SWT.CHECK);
        importSkipEnabledCheck.setSelection(config.isImportSkipEnabled());
        
        // 导入跳过前缀
        new Label(commonComposite, SWT.NONE).setText("Import Skip Prefixes:");
        importSkipPrefixesText = new Text(commonComposite, SWT.BORDER);
        importSkipPrefixesText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        importSkipPrefixesText.setText(config.getImportSkipPrefixes());
        
        // 导入保留前缀
        new Label(commonComposite, SWT.NONE).setText("Import Keep Prefixes:");
        importKeepPrefixesText = new Text(commonComposite, SWT.BORDER);
        importKeepPrefixesText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        importKeepPrefixesText.setText(config.getImportKeepPrefixes());
        
        // 显示错误堆栈
        new Label(commonComposite, SWT.NONE).setText("Show Error Stacktrace:");
        showErrorStacktraceCheck = new Button(commonComposite, SWT.CHECK);
        showErrorStacktraceCheck.setSelection(config.isShowErrorStacktrace());
    }
    
    private void createDirectoryAnalysisTab(TabFolder tabFolder) {
        TabItem dirTab = new TabItem(tabFolder, SWT.NONE);
        dirTab.setText("Directory Analysis");
        
        Composite dirComposite = new Composite(tabFolder, SWT.NONE);
        dirComposite.setLayout(new GridLayout(2, false));
        dirTab.setControl(dirComposite);
        
        // 目录路径
        new Label(dirComposite, SWT.NONE).setText("Directory Path:");
        directoryPathText = new Text(dirComposite, SWT.BORDER);
        directoryPathText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        directoryPathText.setText(config.getDirectoryPath());
        
        // 简化方法
        new Label(dirComposite, SWT.NONE).setText("Simplify Methods:");
        simplifyMethodsCheck = new Button(dirComposite, SWT.CHECK);
        simplifyMethodsCheck.setSelection(config.isSimplifyMethods());
        
        // 新添加：Checkbox 用于选择是否作为 Java 工程分析
        new Label(dirComposite, SWT.NONE).setText("As Java Project Analysis (Requires Project Root):");
        isJavaProjectCheck = new Button(dirComposite, SWT.CHECK);
        isJavaProjectCheck.setSelection(config.isJavaAnalysis());  // 从 config 读取初始值，默认 true
        isJavaProjectCheck.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        isJavaProjectCheck.setToolTipText("If selected, will infer project root for full Java dependency analysis; otherwise, only analyze directory contents.");
        
        // 新添加：Checkbox 是否生成后打开所在目录
        new Label(dirComposite, SWT.NONE).setText("Open Output Directory After Generation:");
        openOutputDirectoryCheck = new Button(dirComposite, SWT.CHECK);
        openOutputDirectoryCheck.setSelection(config.isOpenOutputDirectory());  // 从 config 读取初始值，默认 true
        openOutputDirectoryCheck.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        openOutputDirectoryCheck.setToolTipText("");
        
        // 包含文件
        new Label(dirComposite, SWT.NONE).setText("Include Files:");
        directoryIncludeFilesText = new Text(dirComposite, SWT.BORDER);
        directoryIncludeFilesText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        directoryIncludeFilesText.setText(config.getDirectoryIncludeFiles());
        
        // 排除文件
        new Label(dirComposite, SWT.NONE).setText("Exclude Files:");
        directoryExcludeFilesText = new Text(dirComposite, SWT.BORDER);
        directoryExcludeFilesText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        directoryExcludeFilesText.setText(config.getDirectoryExcludeFiles());
        
        // 包含目录
        new Label(dirComposite, SWT.NONE).setText("Include Folders:");
        directoryIncludeFoldersText = new Text(dirComposite, SWT.BORDER);
        directoryIncludeFoldersText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        directoryIncludeFoldersText.setText(config.getDirectoryIncludeFolders());
        
        // 排除目录
        new Label(dirComposite, SWT.NONE).setText("Exclude Folders:");
        directoryExcludeFoldersText = new Text(dirComposite, SWT.BORDER);
        directoryExcludeFoldersText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        directoryExcludeFoldersText.setText(config.getDirectoryExcludeFolders());
        
        // 允许的扩展名
        new Label(dirComposite, SWT.NONE).setText("Allowed Extensions:");
        directoryAllowedExtensionsText = new Text(dirComposite, SWT.BORDER);
        directoryAllowedExtensionsText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        directoryAllowedExtensionsText.setText(config.getDirectoryAllowedExtensions());
    }
    
    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
        createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
    }
    
    @Override
    protected void okPressed() {
        // 保存配置
        if (isClassMode) {
            config.setMainClass(mainClassText.getText());
            config.setProjectRoot(projectRootText.getText());
            config.setProjectPackagePrefixes(projectPackagePrefixesText.getText());
            try {
                config.setMethodBodyMaxDepth(Integer.parseInt(methodBodyMaxDepthText.getText()));
            } catch (NumberFormatException e) {
                config.setMethodBodyMaxDepth(1);
            }
            config.setKeepOnlyReferencedMethods(keepOnlyReferencedMethodsCheck.getSelection());
            config.setShowRemovedMethods(showRemovedMethodsCheck.getSelection());
            config.setSourceDirectories(sourceDirectoriesText.getText());
        }
        
        config.setOutputFile(outputFileText.getText());
        try {
            config.setMaxDepth(Integer.parseInt(maxDepthText.getText()));
        } catch (NumberFormatException e) {
            config.setMaxDepth(2);
        }
        config.setExcludedPackages(excludedPackagesText.getText());
        config.setMethodExceptions(methodExceptionsText.getText());
        try {
            config.setContentSizeThreshold(Integer.parseInt(contentSizeThresholdText.getText()));
        } catch (NumberFormatException e) {
            config.setContentSizeThreshold(100000);
        }
        config.setOmitBeanMethods(omitBeanMethodsCheck.getSelection());
        config.setShowOmittedAccessors(showOmittedAccessorsCheck.getSelection());
        config.setImportSkipEnabled(importSkipEnabledCheck.getSelection());
        config.setImportSkipPrefixes(importSkipPrefixesText.getText());
        config.setImportKeepPrefixes(importKeepPrefixesText.getText());
        config.setShowErrorStacktrace(showErrorStacktraceCheck.getSelection());
        
        if (!isClassMode) {
            config.setDirectoryPath(directoryPathText.getText());
            config.setSimplifyMethods(simplifyMethodsCheck.getSelection());
            config.setDirectoryIncludeFiles(directoryIncludeFilesText.getText());
            config.setDirectoryExcludeFiles(directoryExcludeFilesText.getText());
            config.setDirectoryIncludeFolders(directoryIncludeFoldersText.getText());
            config.setDirectoryExcludeFolders(directoryExcludeFoldersText.getText());
            config.setDirectoryAllowedExtensions(directoryAllowedExtensionsText.getText());
            
            // 新添加：保存 Checkbox 状态
            config.setJavaAnalysis(isJavaProjectCheck.getSelection());
        }
        
        super.okPressed();
    }
    
    public PluginConfig getConfig() {
        return config;
    }
}