package io.github.jitawangzi.jdepend.eclipse.config;

/**
 * 插件配置类
 */
public class PluginConfig {
    
    // 通用配置
    private String outputFile = "output.md";
    private int maxDepth = 2;
    private String excludedPackages = "";
    private String methodExceptions = "";
    private int contentSizeThreshold = 100000;
    private boolean omitBeanMethods = false;
    private boolean showOmittedAccessors = false;
    private boolean importSkipEnabled = false;
    private String importSkipPrefixes = "";
    private String importKeepPrefixes = "io.vertx,io.github,org.apache,org.slf4j,org.springframework,com.google,javax.";
    private boolean showErrorStacktrace = false;
    
    // 类分析配置
    private String mainClass = "";
    private String projectRoot = "";
    private String projectPackagePrefixes = "";
    private int methodBodyMaxDepth = 1;
    private boolean keepOnlyReferencedMethods = true;
    private boolean showRemovedMethods = false;
    private String sourceDirectories = "src";
    
    // 目录分析配置
    private String directoryPath = "";
    private boolean simplifyMethods = false;
    private String directoryIncludeFiles = "";
    private String directoryExcludeFiles = "";
    private String directoryIncludeFolders = "";
    private String directoryExcludeFolders = "";
    private String directoryAllowedExtensions = ".java,.js,.ts,.py,.cpp,.c,.h,.hpp,.cs,.php,.rb,.go,.rs,.kt,.scala,.groovy";
    
    // 默认构造函数
    public PluginConfig() {
    }
    
    // 设置输出文件的完整路径
    public void setOutputFileWithProjectPath(String projectRoot, String fileName) {
        if (projectRoot != null && !projectRoot.trim().isEmpty()) {
            this.outputFile = projectRoot + java.io.File.separator + fileName;
        } else {
            this.outputFile = fileName;
        }
    }
    
    // 获取输出文件的绝对路径
    public String getAbsoluteOutputFile() {
        java.io.File file = new java.io.File(outputFile);
        if (file.isAbsolute()) {
            return outputFile;
        } else {
            // 如果是相对路径，相对于项目根目录
            if (projectRoot != null && !projectRoot.trim().isEmpty()) {
                return projectRoot + java.io.File.separator + outputFile;
            } else if (directoryPath != null && !directoryPath.trim().isEmpty()) {
                // 目录分析模式，相对于目录路径的父目录
                java.io.File dirFile = new java.io.File(directoryPath);
                java.io.File parentDir = dirFile.getParentFile();
                if (parentDir != null) {
                    return parentDir.getAbsolutePath() + java.io.File.separator + outputFile;
                }
            }
            return new java.io.File(outputFile).getAbsolutePath();
        }
    }
    
    // Getters and Setters
    public String getOutputFile() {
        return outputFile;
    }
    
    public void setOutputFile(String outputFile) {
        this.outputFile = outputFile;
    }
    
    public int getMaxDepth() {
        return maxDepth;
    }
    
    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }
    
    public String getExcludedPackages() {
        return excludedPackages;
    }
    
    public void setExcludedPackages(String excludedPackages) {
        this.excludedPackages = excludedPackages;
    }
    
    public String getMethodExceptions() {
        return methodExceptions;
    }
    
    public void setMethodExceptions(String methodExceptions) {
        this.methodExceptions = methodExceptions;
    }
    
    public int getContentSizeThreshold() {
        return contentSizeThreshold;
    }
    
    public void setContentSizeThreshold(int contentSizeThreshold) {
        this.contentSizeThreshold = contentSizeThreshold;
    }
    
    public boolean isOmitBeanMethods() {
        return omitBeanMethods;
    }
    
    public void setOmitBeanMethods(boolean omitBeanMethods) {
        this.omitBeanMethods = omitBeanMethods;
    }
    
    public boolean isShowOmittedAccessors() {
        return showOmittedAccessors;
    }
    
    public void setShowOmittedAccessors(boolean showOmittedAccessors) {
        this.showOmittedAccessors = showOmittedAccessors;
    }
    
    public boolean isImportSkipEnabled() {
        return importSkipEnabled;
    }
    
    public void setImportSkipEnabled(boolean importSkipEnabled) {
        this.importSkipEnabled = importSkipEnabled;
    }
    
    public String getImportSkipPrefixes() {
        return importSkipPrefixes;
    }
    
    public void setImportSkipPrefixes(String importSkipPrefixes) {
        this.importSkipPrefixes = importSkipPrefixes;
    }
    
    public String getImportKeepPrefixes() {
        return importKeepPrefixes;
    }
    
    public void setImportKeepPrefixes(String importKeepPrefixes) {
        this.importKeepPrefixes = importKeepPrefixes;
    }
    
    public boolean isShowErrorStacktrace() {
        return showErrorStacktrace;
    }
    
    public void setShowErrorStacktrace(boolean showErrorStacktrace) {
        this.showErrorStacktrace = showErrorStacktrace;
    }
    
    public String getMainClass() {
        return mainClass;
    }
    
    public void setMainClass(String mainClass) {
        this.mainClass = mainClass;
    }
    
    public String getProjectRoot() {
        return projectRoot;
    }
    
    public void setProjectRoot(String projectRoot) {
        this.projectRoot = projectRoot;
    }
    
    public String getProjectPackagePrefixes() {
        return projectPackagePrefixes;
    }
    
    public void setProjectPackagePrefixes(String projectPackagePrefixes) {
        this.projectPackagePrefixes = projectPackagePrefixes;
    }
    
    public int getMethodBodyMaxDepth() {
        return methodBodyMaxDepth;
    }
    
    public void setMethodBodyMaxDepth(int methodBodyMaxDepth) {
        this.methodBodyMaxDepth = methodBodyMaxDepth;
    }
    
    public boolean isKeepOnlyReferencedMethods() {
        return keepOnlyReferencedMethods;
    }
    
    public void setKeepOnlyReferencedMethods(boolean keepOnlyReferencedMethods) {
        this.keepOnlyReferencedMethods = keepOnlyReferencedMethods;
    }
    
    public boolean isShowRemovedMethods() {
        return showRemovedMethods;
    }
    
    public void setShowRemovedMethods(boolean showRemovedMethods) {
        this.showRemovedMethods = showRemovedMethods;
    }
    
    public String getSourceDirectories() {
        return sourceDirectories;
    }
    
    public void setSourceDirectories(String sourceDirectories) {
        this.sourceDirectories = sourceDirectories;
    }
    
    public String getDirectoryPath() {
        return directoryPath;
    }
    
    public void setDirectoryPath(String directoryPath) {
        this.directoryPath = directoryPath;
    }
    
    public boolean isSimplifyMethods() {
        return simplifyMethods;
    }
    
    public void setSimplifyMethods(boolean simplifyMethods) {
        this.simplifyMethods = simplifyMethods;
    }
    
    public String getDirectoryIncludeFiles() {
        return directoryIncludeFiles;
    }
    
    public void setDirectoryIncludeFiles(String directoryIncludeFiles) {
        this.directoryIncludeFiles = directoryIncludeFiles;
    }
    
    public String getDirectoryExcludeFiles() {
        return directoryExcludeFiles;
    }
    
    public void setDirectoryExcludeFiles(String directoryExcludeFiles) {
        this.directoryExcludeFiles = directoryExcludeFiles;
    }
    
    public String getDirectoryIncludeFolders() {
        return directoryIncludeFolders;
    }
    
    public void setDirectoryIncludeFolders(String directoryIncludeFolders) {
        this.directoryIncludeFolders = directoryIncludeFolders;
    }
    
    public String getDirectoryExcludeFolders() {
        return directoryExcludeFolders;
    }
    
    public void setDirectoryExcludeFolders(String directoryExcludeFolders) {
        this.directoryExcludeFolders = directoryExcludeFolders;
    }
    
    public String getDirectoryAllowedExtensions() {
        return directoryAllowedExtensions;
    }
    
    public void setDirectoryAllowedExtensions(String directoryAllowedExtensions) {
        this.directoryAllowedExtensions = directoryAllowedExtensions;
    }
}

