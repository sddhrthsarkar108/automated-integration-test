package com.sbtl1.mod1.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple code flow analyzer using regular expressions.
 * This is a lightweight alternative to JavaParser that works with Java 8.
 */
public class SimpleCodeFlowAnalyzer {
    private final String sourceRootPath;
    private final Map<String, List<String>> methodCalls = new HashMap<>();
    private final Map<String, String> knownTypes = new HashMap<>();
    private final Set<String> visitedFiles = new HashSet<>();
    
    public SimpleCodeFlowAnalyzer() {
        // Determine the source root path
        String rootPath = System.getProperty("user.dir");
        File projectRoot = new File(rootPath);
        
        // Try various common source paths
        File[] possibleSourcePaths = {
            new File(projectRoot, "mod1/src/main/java"),
            new File(projectRoot, "src/main/java"),
            new File(projectRoot.getParentFile(), "mod1/src/main/java"),
            new File(projectRoot.getParentFile(), "src/main/java")
        };
        
        File sourceRoot = null;
        for (File path : possibleSourcePaths) {
            if (path.exists() && path.isDirectory()) {
                sourceRoot = path;
                break;
            }
        }
        
        if (sourceRoot == null) {
            // Default fallback
            sourceRoot = new File(rootPath, "mod1/src/main/java");
        }
        
        this.sourceRootPath = sourceRoot.getAbsolutePath();
        System.out.println("Source root path: " + sourceRootPath);
        
        // Initialize with known package patterns for Spring applications
        knownTypes.put("Service", "service");
        knownTypes.put("Repository", "dao");
        knownTypes.put("Controller", "rest");
        knownTypes.put("Entity", "entities");
    }
    
    /**
     * Analyzes the call flow starting from the specified method
     */
    public CallGraph analyzeCallFlow(String className, String methodName) {
        try {
            System.out.println("DEBUG: Starting analysis of " + className + "." + methodName + " from path: " + sourceRootPath);
            
            // Check if the file exists before attempting analysis
            String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
            String packageName = className.substring(0, className.lastIndexOf('.'));
            String packagePath = packageName.replace('.', File.separatorChar);
            String filePath = Paths.get(sourceRootPath, packagePath, simpleClassName + ".java").toString();
            
            File classFile = new File(filePath);
            if (!classFile.exists()) {
                System.err.println("DEBUG: Class file not found at path: " + filePath);
                System.err.println("DEBUG: Absolute path: " + classFile.getAbsolutePath());
                // Search for the file recursively to help debug path issues
                System.out.println("DEBUG: Searching for " + simpleClassName + ".java in parent directories...");
                File root = new File(System.getProperty("user.dir"));
                findFile(root, simpleClassName + ".java");
            }
            
            Set<String> visitedMethods = new HashSet<>();
            CallGraph callGraph = new CallGraph();
            callGraph.setStartClassName(className);
            callGraph.setStartMethodName(methodName);
            
            // Find method calls recursively
            findMethodCalls(className, methodName, callGraph, visitedMethods);
            
            System.out.println("DEBUG: Analysis completed successfully for " + className + "." + methodName);
            return callGraph;
        } catch (Exception e) {
            System.err.println("ERROR in analyzeCallFlow: " + e.getMessage());
            e.printStackTrace();
            // Return an empty call graph to avoid null pointer exceptions
            CallGraph emptyGraph = new CallGraph();
            emptyGraph.setStartClassName(className);
            emptyGraph.setStartMethodName(methodName);
            return emptyGraph;
        }
    }
    
    /**
     * Recursively finds method calls
     */
    private void findMethodCalls(String className, String methodName, CallGraph callGraph, Set<String> visitedMethods) {
        String methodSignature = className + "." + methodName;
        if (visitedMethods.contains(methodSignature)) {
            return; // Avoid infinite recursion
        }
        visitedMethods.add(methodSignature);
        
        try {
            // Get class file path
            String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
            String packageName = className.substring(0, className.lastIndexOf('.'));
            String packagePath = packageName.replace('.', File.separatorChar);
            String filePath = Paths.get(sourceRootPath, packagePath, simpleClassName + ".java").toString();
            
            System.out.println("Analyzing file: " + filePath);
            
            File file = new File(filePath);
            if (!file.exists()) {
                System.err.println("File not found: " + filePath);
                return;
            }
            
            // Read the file and extract the class details
            String fileContent = readFile(filePath);
            visitedFiles.add(filePath);
            
            // Check if this is an interface
            boolean isInterface = fileContent.contains("interface " + simpleClassName);
            
            // Handle interfaces differently
            if (isInterface) {
                handleInterfaceMethod(className, methodName, fileContent, callGraph, visitedMethods);
                return;
            }
            
            // Extract the method code for a regular class
            String methodCode = extractMethodCode(fileContent, methodName);
            if (methodCode.isEmpty()) {
                System.err.println("Method not found: " + methodName + " in class " + className);
                
                // Try to find inherited method in superclass
                String superClass = findSuperClass(fileContent);
                if (superClass != null && !superClass.equals("Object")) {
                    System.out.println("Checking superclass: " + superClass);
                    findMethodCalls(superClass, methodName, callGraph, visitedMethods);
                }
                return;
            }
            
            // Find all field dependencies in the class
            Map<String, String> fieldTypes = findFieldTypes(fileContent, className);
            
            // Find method calls in the method code
            List<MethodCall> calls = findMethodCallsInCode(methodCode, className, fieldTypes);
            callGraph.addNode(className, methodName, calls);
            
            // Recursively find method calls for each callee
            for (MethodCall call : calls) {
                findMethodCalls(call.className, call.methodName, callGraph, visitedMethods);
            }
            
        } catch (IOException e) {
            System.err.println("Error analyzing file for " + className + "." + methodName + ": " + e.getMessage());
        }
    }
    
    /**
     * Handle methods in interfaces, including Spring Data JPA repositories
     */
    private void handleInterfaceMethod(String className, String methodName, String fileContent, 
                                     CallGraph callGraph, Set<String> visitedMethods) {
        // For JPA Repository methods, we need special handling
        if (className.contains("Repository") && fileContent.contains("extends")) {
            // Check if this is a Spring Data Repository
            if (fileContent.contains("JpaRepository") || fileContent.contains("CrudRepository") || 
                fileContent.contains("PagingAndSortingRepository") || fileContent.contains("Repository")) {
                
                System.out.println("Handling JPA Repository method: " + methodName);
                
                // For derived query methods like findByXyz
                if (methodName.startsWith("findBy") || methodName.startsWith("getBy") || 
                    methodName.startsWith("queryBy") || methodName.startsWith("searchBy") ||
                    methodName.startsWith("countBy") || methodName.startsWith("existsBy")) {
                    
                    // JPA executes the query directly to the database
                    List<MethodCall> calls = new ArrayList<>();
                    callGraph.addNode(className, methodName, calls);
                    System.out.println("JPA Repository method: Executes direct database query");
                    return;
                }
                
                // For CRUD operations, they're implemented in SimpleJpaRepository
                if (methodName.equals("save") || methodName.equals("saveAll") || 
                    methodName.equals("findById") || methodName.equals("findAll") || 
                    methodName.equals("deleteById") || methodName.equals("delete") || 
                    methodName.equals("count") || methodName.equals("existsById")) {
                    
                    List<MethodCall> calls = new ArrayList<>();
                    callGraph.addNode(className, methodName, calls);
                    System.out.println("JPA Repository method: Implemented by SimpleJpaRepository");
                    return;
                }
                
                // Try to find custom query with @Query annotation
                Pattern queryPattern = Pattern.compile("@Query[^)]*\\)\\s*" + 
                                                      "(?:[\\w<>\\[\\],\\s]+)\\s+" + 
                                                      Pattern.quote(methodName) + "\\s*\\(");
                Matcher queryMatcher = queryPattern.matcher(fileContent);
                if (queryMatcher.find()) {
                    List<MethodCall> calls = new ArrayList<>();
                    callGraph.addNode(className, methodName, calls);
                    System.out.println("JPA Repository method: Custom query with @Query annotation");
                    return;
                }
            }
        }
        
        // Look for default methods in the interface
        String methodCode = extractMethodCode(fileContent, "default " + methodName);
        if (!methodCode.isEmpty()) {
            // Handle default method implementation
            System.out.println("Found default method implementation in interface");
            Map<String, String> fieldTypes = findFieldTypes(fileContent, className);
            List<MethodCall> calls = findMethodCallsInCode(methodCode, className, fieldTypes);
            callGraph.addNode(className, methodName, calls);
            
            // Recursively find method calls for each callee
            for (MethodCall call : calls) {
                findMethodCalls(call.className, call.methodName, callGraph, visitedMethods);
            }
            return;
        }
        
        // Find all implementors of this interface in the codebase
        List<String> implementors = findImplementors(className);
        if (!implementors.isEmpty()) {
            System.out.println("Found implementors of " + className + ": " + implementors);
            
            // Add a node for the interface method
            callGraph.addNode(className, methodName, new ArrayList<>());
            
            // Check each implementor for the method
            for (String implementor : implementors) {
                findMethodCalls(implementor, methodName, callGraph, visitedMethods);
            }
        } else {
            // For unknown implementations, we just add a placeholder
            System.out.println("No implementors found for interface " + className);
            callGraph.addNode(className, methodName, new ArrayList<>());
        }
    }
    
    /**
     * Find all classes that implement a given interface
     */
    private List<String> findImplementors(String interfaceName) {
        List<String> implementors = new ArrayList<>();
        String simpleInterfaceName = interfaceName.substring(interfaceName.lastIndexOf('.') + 1);
        
        // Search for classes implementing the interface in files we've already visited
        for (String filePath : visitedFiles) {
            try {
                String fileContent = readFile(filePath);
                if (fileContent.contains("implements") && fileContent.contains(simpleInterfaceName)) {
                    // Extract class name from the file path
                    String packagePath = filePath.replace(sourceRootPath + File.separator, "")
                                                 .replace(".java", "")
                                                 .replace(File.separator, ".");
                    implementors.add(packagePath);
                }
            } catch (IOException e) {
                System.err.println("Error reading potential implementor file: " + filePath);
            }
        }
        
        return implementors;
    }
    
    /**
     * Find the superclass of a given class
     */
    private String findSuperClass(String fileContent) {
        Pattern extendsPattern = Pattern.compile("class\\s+\\w+\\s+extends\\s+(\\w+)");
        Matcher extendsMatcher = extendsPattern.matcher(fileContent);
        
        if (extendsMatcher.find()) {
            String superClass = extendsMatcher.group(1);
            
            // Try to resolve the full class name
            if (!superClass.contains(".")) {
                // Check imports for this class
                Pattern importPattern = Pattern.compile("import\\s+([^;]+\\." + superClass + ");");
                Matcher importMatcher = importPattern.matcher(fileContent);
                
                if (importMatcher.find()) {
                    return importMatcher.group(1);
                }
            }
            
            return superClass;
        }
        
        return null;
    }
    
    /**
     * Read the entire content of a file
     */
    private String readFile(String filePath) throws IOException {
        StringBuilder fileContent = new StringBuilder();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                fileContent.append(line).append("\n");
            }
        }
        
        return fileContent.toString();
    }
    
    /**
     * Extracts method code from a file's content
     */
    private String extractMethodCode(String fileContent, String methodName) {
        // Find the method in the file - handle methods with access modifiers or default methods
        Pattern methodPattern = Pattern.compile(
                "(?:public|private|protected|default|)\\s+(?:static\\s+)?(?:[\\w<>\\[\\],\\s]+)\\s+" + 
                Pattern.quote(methodName.replace("default ", "")) + "\\s*\\([^)]*\\)\\s*(?:throws[^{]+)?\\{", 
                Pattern.DOTALL);
        
        Matcher methodMatcher = methodPattern.matcher(fileContent);
        if (methodMatcher.find()) {
            int startPos = methodMatcher.start();
            
            // Find the matching closing brace
            int pos = methodMatcher.end();
            int braceCount = 1;
            
            while (braceCount > 0 && pos < fileContent.length()) {
                char c = fileContent.charAt(pos);
                if (c == '{') braceCount++;
                else if (c == '}') braceCount--;
                pos++;
            }
            
            if (braceCount == 0) {
                return fileContent.substring(startPos, pos);
            }
        }
        
        return "";
    }
    
    /**
     * Finds method calls in code using regular expressions
     */
    private List<MethodCall> findMethodCallsInCode(String code, String callerClassName, Map<String, String> fieldTypes) {
        List<MethodCall> calls = new ArrayList<>();
        
        // Find method calls
        Pattern pattern = Pattern.compile("(\\w+)\\.(\\w+)\\s*\\(");
        Matcher matcher = pattern.matcher(code);
        
        while (matcher.find()) {
            String objectName = matcher.group(1);
            String methodName = matcher.group(2);
            
            // Skip common Java method calls
            if (isCommonMethod(methodName)) continue;
            
            // Find the class type for this object
            String className = fieldTypes.get(objectName);
            if (className != null) {
                calls.add(new MethodCall(objectName, className, methodName));
            }
        }
        
        // Find static method calls
        Pattern staticPattern = Pattern.compile("(\\w+)\\.(\\w+)\\.(\\w+)\\s*\\(");
        Matcher staticMatcher = staticPattern.matcher(code);
        
        while (staticMatcher.find()) {
            String packageName = staticMatcher.group(1);
            String className = staticMatcher.group(2);
            String methodName = staticMatcher.group(3);
            
            // Skip common Java static methods
            if (isCommonMethod(methodName)) continue;
            
            // Try to resolve the full class name
            String fullClassName = resolveFullClassName(packageName + "." + className, callerClassName);
            if (fullClassName != null) {
                calls.add(new MethodCall(className, fullClassName, methodName));
            }
        }
        
        return calls;
    }
    
    /**
     * Finds field types in the class
     */
    private Map<String, String> findFieldTypes(String code, String className) {
        Map<String, String> fieldTypes = new HashMap<>();
        
        // First, add "this" as a field of the current class type
        fieldTypes.put("this", className);
        
        // 1. Find autowired/injected fields
        Pattern fieldPattern = Pattern.compile(
                "@Autowired[\\s\\n]*(?:private|protected|public)\\s+([\\w<>\\[\\],\\s]+)\\s+(\\w+)\\s*;");
        Matcher fieldMatcher = fieldPattern.matcher(code);
        
        while (fieldMatcher.find()) {
            String fieldType = fieldMatcher.group(1).trim();
            String fieldName = fieldMatcher.group(2).trim();
            
            // Map field names to their types
            String fullClassName = resolveFullClassName(fieldType, className);
            if (fullClassName != null) {
                fieldTypes.put(fieldName, fullClassName);
                System.out.println("Found autowired field: " + fieldName + " of type " + fullClassName);
            }
        }
        
        // 2. Find constructor injected fields
        Pattern constructorPattern = Pattern.compile(
                "public\\s+" + className.substring(className.lastIndexOf('.') + 1) + 
                "\\s*\\(([^)]+)\\)\\s*\\{([^}]+)\\}");
        Matcher constructorMatcher = constructorPattern.matcher(code);
        
        if (constructorMatcher.find()) {
            String constructorParams = constructorMatcher.group(1);
            String constructorBody = constructorMatcher.group(2);
            
            // Parse constructor parameters
            String[] params = constructorParams.split(",");
            for (String param : params) {
                param = param.trim();
                String[] parts = param.split("\\s+");
                if (parts.length >= 2) {
                    String paramType = parts[0].trim();
                    String paramName = parts[1].trim();
                    
                    // Check for field assignments in constructor body
                    Pattern assignPattern = Pattern.compile(
                            "this\\.(\\w+)\\s*=\\s*" + paramName + "\\s*;");
                    Matcher assignMatcher = assignPattern.matcher(constructorBody);
                    
                    if (assignMatcher.find()) {
                        String fieldName = assignMatcher.group(1);
                        String fullClassName = resolveFullClassName(paramType, className);
                        if (fullClassName != null) {
                            fieldTypes.put(fieldName, fullClassName);
                            System.out.println("Found constructor-injected field: " + fieldName + " of type " + fullClassName);
                        }
                    }
                }
            }
        }
        
        // 3. Find other fields with non-primitive types
        Pattern otherFieldPattern = Pattern.compile(
                "(?:private|protected|public)\\s+([\\w<>\\[\\],\\s]+)\\s+(\\w+)\\s*;");
        Matcher otherFieldMatcher = otherFieldPattern.matcher(code);
        
        while (otherFieldMatcher.find()) {
            String fieldType = otherFieldMatcher.group(1).trim();
            String fieldName = otherFieldMatcher.group(2).trim();
            
            // Skip if already found through autowiring or constructor injection
            if (fieldTypes.containsKey(fieldName)) continue;
            
            // Map field names to their types, excluding primitives
            if (!isPrimitiveOrCommonType(fieldType)) {
                String fullClassName = resolveFullClassName(fieldType, className);
                if (fullClassName != null) {
                    fieldTypes.put(fieldName, fullClassName);
                    System.out.println("Found regular field: " + fieldName + " of type " + fullClassName);
                }
            }
        }
        
        return fieldTypes;
    }
    
    /**
     * Resolves a full class name based on a simple type name
     */
    private String resolveFullClassName(String typeName, String currentClassName) {
        // Skip if already a full class name
        if (typeName.contains(".")) {
            return typeName;
        }
        
        // Skip primitive types and common Java types
        if (isPrimitiveOrCommonType(typeName)) {
            return null;
        }
        
        // Get the base package of the current class
        String basePackage = currentClassName.substring(0, currentClassName.lastIndexOf('.', currentClassName.lastIndexOf('.') - 1));
        
        // Try to determine the package based on naming conventions
        for (Map.Entry<String, String> entry : knownTypes.entrySet()) {
            if (typeName.contains(entry.getKey())) {
                return basePackage + "." + entry.getValue() + "." + typeName;
            }
        }
        
        // Default to same package as the current class
        return currentClassName.substring(0, currentClassName.lastIndexOf('.')) + "." + typeName;
    }
    
    /**
     * Checks if a type is a primitive type or common Java type
     */
    private boolean isPrimitiveOrCommonType(String typeName) {
        String[] primitiveAndCommonTypes = {
            "int", "boolean", "char", "byte", "short", "long", "float", "double", "void",
            "String", "Integer", "Boolean", "Character", "Byte", "Short", "Long", "Float", "Double",
            "Object", "Class", "List", "Map", "Set", "Collection", "Iterable", "Iterator",
            "ArrayList", "HashMap", "HashSet", "LinkedList"
        };
        
        for (String type : primitiveAndCommonTypes) {
            if (type.equals(typeName) || 
                typeName.startsWith("List<") || 
                typeName.startsWith("Map<") || 
                typeName.startsWith("Set<")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if a method name is a common Java method to filter out non-application methods
     */
    private boolean isCommonMethod(String methodName) {
        String[] commonMethods = {
                "equals", "hashCode", "toString", "clone", "getClass", "notify", "notifyAll", "wait",
                "get", "set", "add", "remove", "size", "isEmpty", "contains", "forEach", "stream", 
                "of", "format", "valueOf", "parse", "compareTo", "iterator", "hasNext", "next"
        };
        
        for (String method : commonMethods) {
            if (method.equals(methodName)) return true;
        }
        
        return false;
    }
    
    /**
     * Represents a method call
     */
    public static class MethodCall {
        public final String objectName;
        public final String className;
        public final String methodName;
        
        public MethodCall(String objectName, String className, String methodName) {
            this.objectName = objectName;
            this.className = className;
            this.methodName = methodName;
        }
        
        @Override
        public String toString() {
            return objectName + "(" + className + ")." + methodName;
        }
    }
    
    /**
     * Represents a call graph
     */
    public static class CallGraph {
        private String startClassName;
        private String startMethodName;
        private final Map<String, Map<String, List<MethodCall>>> graph = new HashMap<>();
        
        public void setStartClassName(String className) {
            this.startClassName = className;
        }
        
        public void setStartMethodName(String methodName) {
            this.startMethodName = methodName;
        }
        
        public void addNode(String className, String methodName, List<MethodCall> calls) {
            Map<String, List<MethodCall>> classMethods = graph.get(className);
            if (classMethods == null) {
                classMethods = new HashMap<>();
                graph.put(className, classMethods);
            }
            classMethods.put(methodName, calls);
        }
        
        public List<MethodCall> getCalls(String className, String methodName) {
            Map<String, List<MethodCall>> classMethods = graph.get(className);
            if (classMethods != null) {
                return classMethods.get(methodName);
            }
            return Collections.emptyList();
        }
        
        public Set<String> getClasses() {
            return graph.keySet();
        }
        
        /**
         * Prints the call graph in a tree format
         */
        public void print() {
            System.out.println("\nCall Graph for " + startClassName + "." + startMethodName + ":");
            printNode(startClassName, startMethodName, new HashSet<>(), 0);
        }
        
        /**
         * Recursively prints a node in the call graph
         */
        private void printNode(String className, String methodName, Set<String> visited, int depth) {
            String signature = className + "." + methodName;
            if (visited.contains(signature)) {
                indent(depth);
                System.out.println(signature + " (recursive call)");
                return;
            }
            
            visited.add(signature);
            
            indent(depth);
            System.out.println(signature);
            
            List<MethodCall> calls = getCalls(className, methodName);
            if (calls != null) {
                for (MethodCall call : calls) {
                    printNode(call.className, call.methodName, visited, depth + 1);
                }
            }
        }
        
        /**
         * Prints indentation for the given depth
         */
        private void indent(int depth) {
            for (int i = 0; i < depth; i++) {
                System.out.print("  ");
            }
        }

        public String getStartClassName() {
            return startClassName;
        }

        public String getStartMethodName() {
            return startMethodName;
        }

        /**
         * Get all method calls in the call graph
         */
        public List<MethodCall> getAllCalls() {
            List<MethodCall> allCalls = new ArrayList<>();
            for (String className : graph.keySet()) {
                Map<String, List<MethodCall>> classMethods = graph.get(className);
                for (String methodName : classMethods.keySet()) {
                    allCalls.addAll(classMethods.get(methodName));
                }
            }
            return allCalls;
        }
    }

    // Helper method to recursively find a file
    private void findFile(File directory, String fileName) {
        if (directory == null || !directory.isDirectory()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    findFile(file, fileName);
                } else if (file.getName().equals(fileName)) {
                    System.out.println("DEBUG: Found file at: " + file.getAbsolutePath());
                }
            }
        }
    }
} 