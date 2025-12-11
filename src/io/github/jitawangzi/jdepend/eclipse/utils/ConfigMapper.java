package io.github.jitawangzi.jdepend.eclipse.utils;

import java.lang.reflect.Field;
import java.util.Properties;
import io.github.jitawangzi.jdepend.eclipse.config.PluginConfig;

/**
 * 配置映射工具类，用于将插件配置转换为原始配置格式
 */
public class ConfigMapper {
    
    /**
     * 将插件配置转换为Properties格式，以便传递给原始的分析工具
     */
    public static Properties mapToProperties(PluginConfig config, boolean isClassMode) {
        Properties props = new Properties();
        
        if (isClassMode) {
            // 类分析模式配置
            props.setProperty("main.class", config.getMainClass());
            props.setProperty("project.root", config.getProjectRoot());
            props.setProperty("project.package.prefixes", config.getProjectPackagePrefixes());
            props.setProperty("method.body.max.depth", String.valueOf(config.getMethodBodyMaxDepth()));
            props.setProperty("keep.only.referenced.methods", String.valueOf(config.isKeepOnlyReferencedMethods()));
            props.setProperty("show.removed.methods", String.valueOf(config.isShowRemovedMethods()));
            props.setProperty("source.directories", config.getSourceDirectories());
        } else {
            // 目录模式配置
            props.setProperty("directory.path", config.getDirectoryPath());
            props.setProperty("directory.include.files", config.getDirectoryIncludeFiles());
            props.setProperty("directory.exclude.files", config.getDirectoryExcludeFiles());
            props.setProperty("directory.include.folders", config.getDirectoryIncludeFolders());
            props.setProperty("directory.exclude.folders", config.getDirectoryExcludeFolders());
            props.setProperty("directory.allowed.extensions", config.getDirectoryAllowedExtensions());
        }
        
        // 通用配置
        props.setProperty("simplify.methods", String.valueOf(config.isSimplifyMethods()));
        props.setProperty("output.file", config.getOutputFile());
        props.setProperty("max.depth", String.valueOf(config.getMaxDepth()));
        props.setProperty("excluded.packages", config.getExcludedPackages());
        props.setProperty("method.exceptions", config.getMethodExceptions());
        props.setProperty("content.size.threshold", String.valueOf(config.getContentSizeThreshold()));
        props.setProperty("omit.bean.methods", String.valueOf(config.isOmitBeanMethods()));
        props.setProperty("show.omitted.accessors", String.valueOf(config.isShowOmittedAccessors()));
        props.setProperty("import.skip.enabled", String.valueOf(config.isImportSkipEnabled()));
        props.setProperty("import.skip.prefixes", config.getImportSkipPrefixes());
        props.setProperty("import.keep.prefixes", config.getImportKeepPrefixes());
        props.setProperty("show.error.stacktrace", String.valueOf(config.isShowErrorStacktrace()));
        
        return props;
    }
    
    /**
     * 通过反射设置原始AppConfig的值
     * 这里假设你的原始AppConfig有类似的setter方法或可访问的字段
     */
    public static void applyToAppConfig(Object appConfig, PluginConfig pluginConfig, boolean isClassMode) {
        try {
            Class<?> appConfigClass = appConfig.getClass();
            
            if (isClassMode) {
                setFieldValue(appConfig, appConfigClass, "mainClass", pluginConfig.getMainClass());
                setFieldValue(appConfig, appConfigClass, "projectRoot", pluginConfig.getProjectRoot());
                setFieldValue(appConfig, appConfigClass, "projectPackagePrefixes", pluginConfig.getProjectPackagePrefixes());
                setFieldValue(appConfig, appConfigClass, "methodBodyMaxDepth", pluginConfig.getMethodBodyMaxDepth());
                setFieldValue(appConfig, appConfigClass, "keepOnlyReferencedMethods", pluginConfig.isKeepOnlyReferencedMethods());
                setFieldValue(appConfig, appConfigClass, "showRemovedMethods", pluginConfig.isShowRemovedMethods());
                setFieldValue(appConfig, appConfigClass, "sourceDirectories", pluginConfig.getSourceDirectories());
            } else {
                setFieldValue(appConfig, appConfigClass, "directoryPath", pluginConfig.getDirectoryPath());
                setFieldValue(appConfig, appConfigClass, "simplifyMethods", pluginConfig.isSimplifyMethods());
                setFieldValue(appConfig, appConfigClass, "directoryIncludeFiles", pluginConfig.getDirectoryIncludeFiles());
                setFieldValue(appConfig, appConfigClass, "directoryExcludeFiles", pluginConfig.getDirectoryExcludeFiles());
                setFieldValue(appConfig, appConfigClass, "directoryIncludeFolders", pluginConfig.getDirectoryIncludeFolders());
                setFieldValue(appConfig, appConfigClass, "directoryExcludeFolders", pluginConfig.getDirectoryExcludeFolders());
                setFieldValue(appConfig, appConfigClass, "directoryAllowedExtensions", pluginConfig.getDirectoryAllowedExtensions());
            }
            
            // 通用配置
            setFieldValue(appConfig, appConfigClass, "outputFile", pluginConfig.getOutputFile());
            setFieldValue(appConfig, appConfigClass, "maxDepth", pluginConfig.getMaxDepth());
            setFieldValue(appConfig, appConfigClass, "excludedPackages", pluginConfig.getExcludedPackages());
            setFieldValue(appConfig, appConfigClass, "methodExceptions", pluginConfig.getMethodExceptions());
            setFieldValue(appConfig, appConfigClass, "contentSizeThreshold", pluginConfig.getContentSizeThreshold());
            setFieldValue(appConfig, appConfigClass, "omitBeanMethods", pluginConfig.isOmitBeanMethods());
            setFieldValue(appConfig, appConfigClass, "showOmittedAccessors", pluginConfig.isShowOmittedAccessors());
            setFieldValue(appConfig, appConfigClass, "importSkipEnabled", pluginConfig.isImportSkipEnabled());
            setFieldValue(appConfig, appConfigClass, "importSkipPrefixes", pluginConfig.getImportSkipPrefixes());
            setFieldValue(appConfig, appConfigClass, "importKeepPrefixes", pluginConfig.getImportKeepPrefixes());
            setFieldValue(appConfig, appConfigClass, "showErrorStacktrace", pluginConfig.isShowErrorStacktrace());
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static void setFieldValue(Object target, Class<?> clazz, String fieldName, Object value) {
        try {
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // 尝试查找对应的setter方法
            try {
                String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
                java.lang.reflect.Method setter = clazz.getMethod(setterName, value.getClass());
                setter.invoke(target, value);
            } catch (Exception ex) {
                System.err.println("Failed to set field " + fieldName + ": " + ex.getMessage());
            }
        }
    }
}

