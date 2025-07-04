package io.github.jitawangzi.jdepend.eclipse.utils;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.*;

/**
 * Eclipse项目工具类
 */
public class EclipseProjectUtils {
    
    /**
     * 从编译单元获取完全限定类名
     */
    public static String getFullyQualifiedClassName(ICompilationUnit unit) {
        try {
            IType[] types = unit.getTypes();
            if (types.length > 0) {
                return types[0].getFullyQualifiedName();
            }
        } catch (JavaModelException e) {
            e.printStackTrace();
        }
        return unit.getElementName().replace(".java", "");
    }
    
    /**
     * 获取项目根目录的绝对路径
     */
    public static String getProjectRootPath(IResource resource) {
        IProject project = resource.getProject();
        IPath location = project.getLocation();
        return location != null ? location.toOSString() : project.getName();
    }
    
    /**
     * 获取资源的绝对路径
     */
    public static String getResourcePath(IResource resource) {
        IPath location = resource.getLocation();
        return location != null ? location.toOSString() : resource.getFullPath().toString();
    }
    
    /**
     * 从Java文件获取包名
     */
    public static String getPackageName(ICompilationUnit unit) {
        try {
            IPackageDeclaration[] packages = unit.getPackageDeclarations();
            if (packages.length > 0) {
                return packages[0].getElementName();
            }
        } catch (JavaModelException e) {
            e.printStackTrace();
        }
        return "";
    }
    
    /**
     * 获取Java项目的源码目录
     */
    public static String getSourceDirectories(ICompilationUnit unit) {
        try {
            IJavaProject javaProject = unit.getJavaProject();
            IClasspathEntry[] classpathEntries = javaProject.getResolvedClasspath(true);
            
            StringBuilder sourceDirs = new StringBuilder();
            for (IClasspathEntry entry : classpathEntries) {
                if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
                    if (sourceDirs.length() > 0) {
                        sourceDirs.append(",");
                    }
                    IPath sourcePath = entry.getPath().removeFirstSegments(1); // 移除项目名
                    sourceDirs.append(sourcePath.toString());
                }
            }
            
            return sourceDirs.length() > 0 ? sourceDirs.toString() : "src/main/java,src/test/java";
        } catch (JavaModelException e) {
            e.printStackTrace();
            return "src/main/java,src/test/java";
        }
    }
    
    /**
     * 从编译单元推断包前缀
     */
    public static String inferPackagePrefixes(ICompilationUnit unit) {
        String packageName = getPackageName(unit);
        if (packageName.isEmpty()) {
            return "";
        }
        
        // 尝试获取顶级包名
        String[] parts = packageName.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        } else if (parts.length == 1) {
            return parts[0];
        }
        
        return packageName;
    }
}

