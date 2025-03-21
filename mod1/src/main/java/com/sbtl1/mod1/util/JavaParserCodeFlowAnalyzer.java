package com.sbtl1.mod1.util;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * A code flow analyzer using JavaParser.
 * This provides a more robust solution compared to regex-based analysis.
 */
public class JavaParserCodeFlowAnalyzer {
    private final String sourceRootPath;
    private final Map<String, Map<String, String>> classFields = new HashMap<>();
    private final Set<String> visitedFiles = new HashSet<>();
    private final JavaParser javaParser;
    private final CombinedTypeSolver typeSolver;

    /**
     * Represents a method call in the code
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
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            MethodCall other = (MethodCall) obj;
            return Objects.equals(className, other.className) && 
                   Objects.equals(methodName, other.methodName);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(className, methodName);
        }
    }
    
    /**
     * Represents a call graph of method invocations
     */
    public static class CallGraph {
        private final Map<String, Map<String, List<MethodCall>>> callMap = new HashMap<>();
        
        public void addNode(String className, String methodName, List<MethodCall> calls) {
            callMap.putIfAbsent(className, new HashMap<>());
            callMap.get(className).put(methodName, calls);
        }
        
        public List<MethodCall> getCalls(String className, String methodName) {
            if (callMap.containsKey(className) && callMap.get(className).containsKey(methodName)) {
                return callMap.get(className).get(methodName);
            }
            return Collections.emptyList();
        }
        
        public Set<String> getClasses() {
            return callMap.keySet();
        }
        
        public Set<String> getMethods(String className) {
            if (callMap.containsKey(className)) {
                return callMap.get(className).keySet();
            }
            return Collections.emptySet();
        }
        
        public List<MethodCall> getAllCalls() {
            List<MethodCall> allCalls = new ArrayList<>();
            for (Map<String, List<MethodCall>> methodMap : callMap.values()) {
                for (List<MethodCall> calls : methodMap.values()) {
                    allCalls.addAll(calls);
                }
            }
            return allCalls;
        }
        
        public Map<String, Map<String, List<MethodCall>>> getCallMap() {
            return callMap;
        }
    }

    public JavaParserCodeFlowAnalyzer() {
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
        
        // Initialize JavaParser with symbol solving capabilities
        this.typeSolver = createTypeSolver();
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        this.javaParser = new JavaParser();
        this.javaParser.getParserConfiguration().setSymbolResolver(symbolSolver);
    }
    
    /**
     * Create a type solver for resolving types in the code
     */
    private CombinedTypeSolver createTypeSolver() {
        CombinedTypeSolver combinedSolver = new CombinedTypeSolver();
        // Add reflection type solver for resolving JDK and library classes
        combinedSolver.add(new ReflectionTypeSolver());
        // Add JavaParser type solver for resolving project source code
        combinedSolver.add(new JavaParserTypeSolver(new File(sourceRootPath)));
        return combinedSolver;
    }
    
    /**
     * Analyzes the call flow of a method
     */
    public CallGraph analyzeCallFlow(String className, String methodName) {
        System.out.println("Starting analysis of " + className + "." + methodName + " from path: " + sourceRootPath);
        
        try {
            CallGraph callGraph = new CallGraph();
            Set<String> visitedMethods = new HashSet<>();
            findMethodCalls(className, methodName, callGraph, visitedMethods);
            return callGraph;
        } catch (Exception e) {
            System.err.println("Error in analysis: " + e.getMessage());
            e.printStackTrace();
            CallGraph emptyGraph = new CallGraph();
            return emptyGraph;
        }
    }
    
    /**
     * Finds method calls from a given method recursively
     */
    private void findMethodCalls(String className, String methodName, CallGraph callGraph, 
                                 Set<String> visitedMethods) {
        String methodSignature = className + "." + methodName;
        if (visitedMethods.contains(methodSignature)) {
            return; // Avoid infinite recursion
        }
        visitedMethods.add(methodSignature);
        
        try {
            // Get file path from class name
            String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
            String packageName = className.substring(0, className.lastIndexOf('.'));
            String packagePath = packageName.replace('.', File.separatorChar);
            Path filePath = Paths.get(sourceRootPath, packagePath, simpleClassName + ".java");
            File file = filePath.toFile();
            
            if (!file.exists()) {
                System.err.println("File not found: " + filePath);
                
                // Try to fix potential package naming issues
                // Check if this is a service/repository class with a missing "modX" part in the package
                if (packageName.contains(".service") || packageName.contains(".dao") || 
                    packageName.contains(".rest") || packageName.contains(".entities")) {
                    
                    // Look for common prefixes in the project
                    String[] commonPrefixes = {
                        "com.sbtl1.mod1", "com.example.mod1", "org.example.mod1",
                        "com.sbtl1", "com.example", "org.example"
                    };
                    
                    for (String prefix : commonPrefixes) {
                        // Extract the component type (service, dao, etc.)
                        int componentTypeIndex = packageName.lastIndexOf('.');
                        if (componentTypeIndex > 0) {
                            String componentType = packageName.substring(componentTypeIndex + 1);
                            // Create a revised package name using the prefix
                            String revisedPackageName = prefix + "." + componentType;
                            String revisedPackagePath = revisedPackageName.replace('.', File.separatorChar);
                            Path revisedFilePath = Paths.get(sourceRootPath, revisedPackagePath, simpleClassName + ".java");
                            File revisedFile = revisedFilePath.toFile();
                            
                            if (revisedFile.exists()) {
                                System.out.println("Found file with revised package path: " + revisedFilePath);
                                file = revisedFile;
                                // Update the class name to reflect the correct package
                                className = revisedPackageName + "." + simpleClassName;
                                break;
                            }
                        }
                    }
                    
                    // If still not found, give up
                    if (!file.exists()) {
                        return;
                    }
                } else {
                    return;
                }
            }
            
            visitedFiles.add(file.toString());
            
            // Parse the Java file
            ParseResult<CompilationUnit> parseResult = javaParser.parse(file);
            if (!parseResult.isSuccessful()) {
                System.err.println("Failed to parse file: " + filePath);
                return;
            }
            
            CompilationUnit cu = parseResult.getResult().get();
            
            // Find the class/interface declaration
            Optional<ClassOrInterfaceDeclaration> classOrInterface = 
                cu.findFirst(ClassOrInterfaceDeclaration.class, 
                             c -> c.getNameAsString().equals(simpleClassName));
            
            if (!classOrInterface.isPresent()) {
                System.err.println("Class or interface not found: " + simpleClassName);
                return;
            }
            
            ClassOrInterfaceDeclaration typeDeclaration = classOrInterface.get();
            boolean isInterface = typeDeclaration.isInterface();
            
            // Store field information for this class
            if (!classFields.containsKey(className)) {
                Map<String, String> fields = analyzeFields(typeDeclaration, className);
                classFields.put(className, fields);
            }
            
            // Handle differently based on whether it's a class or interface
            if (isInterface) {
                handleInterfaceMethod(className, methodName, typeDeclaration, callGraph, visitedMethods);
            } else {
                handleClassMethod(className, methodName, typeDeclaration, callGraph, visitedMethods);
            }
            
        } catch (FileNotFoundException e) {
            System.err.println("Error reading file for " + className + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error analyzing method " + methodSignature + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Handle a method in a class
     */
    private void handleClassMethod(String className, String methodName, 
                                 ClassOrInterfaceDeclaration classDeclaration,
                                 CallGraph callGraph, 
                                 Set<String> visitedMethods) {
        // Find the method in this class
        Optional<MethodDeclaration> methodOpt = classDeclaration.findFirst(MethodDeclaration.class, 
                                                    m -> m.getNameAsString().equals(methodName));
        
        if (methodOpt.isPresent()) {
            // Method found in this class
            MethodDeclaration method = methodOpt.get();
            List<MethodCall> calls = findMethodCallsInMethod(method, className);
            callGraph.addNode(className, methodName, calls);
            
            // Recursively analyze method calls
            for (MethodCall call : calls) {
                findMethodCalls(call.className, call.methodName, callGraph, visitedMethods);
            }
        } else {
            // Method not found in this class, check parent class
            Optional<ClassOrInterfaceType> extendsClause = classDeclaration.getExtendedTypes().stream().findFirst();
            if (extendsClause.isPresent()) {
                String superClassName = resolveClassName(extendsClause.get().getNameAsString(), className);
                if (superClassName != null && !superClassName.equals("java.lang.Object")) {
                    System.out.println("Method not found in class " + className + 
                                     ", checking superclass " + superClassName);
                    findMethodCalls(superClassName, methodName, callGraph, visitedMethods);
                }
            } else {
                // No method found and no superclass to check
                callGraph.addNode(className, methodName, new ArrayList<>());
            }
        }
    }
    
    /**
     * Handle a method in an interface
     */
    private void handleInterfaceMethod(String interfaceName, String methodName, 
                                     ClassOrInterfaceDeclaration interfaceDeclaration,
                                     CallGraph callGraph, 
                                     Set<String> visitedMethods) {
        // Check for default method implementation
        Optional<MethodDeclaration> defaultMethodOpt = interfaceDeclaration.findFirst(MethodDeclaration.class, 
                                                        m -> m.getNameAsString().equals(methodName) && 
                                                             m.isDefault());
        
        if (defaultMethodOpt.isPresent()) {
            // Default method found in interface
            MethodDeclaration defaultMethod = defaultMethodOpt.get();
            List<MethodCall> calls = findMethodCallsInMethod(defaultMethod, interfaceName);
            callGraph.addNode(interfaceName, methodName, calls);
            
            // Recursively analyze method calls
            for (MethodCall call : calls) {
                findMethodCalls(call.className, call.methodName, callGraph, visitedMethods);
            }
            return;
        }
        
        // Check if this is a Spring Data Repository interface
        boolean isSpringDataRepository = isSpringDataRepository(interfaceDeclaration);
        if (isSpringDataRepository) {
            handleSpringDataRepositoryMethod(interfaceName, methodName, interfaceDeclaration, callGraph);
            return;
        }
        
        // For other interfaces, find implementors
        List<String> implementors = findImplementors(interfaceName);
        if (!implementors.isEmpty()) {
            System.out.println("Found implementors of " + interfaceName + ": " + implementors);
            
            // Add a node for the interface method
            callGraph.addNode(interfaceName, methodName, new ArrayList<>());
            
            // Check each implementor for the method
            for (String implementor : implementors) {
                findMethodCalls(implementor, methodName, callGraph, visitedMethods);
            }
        } else {
            // No implementors found
            System.out.println("No implementors found for interface " + interfaceName);
            callGraph.addNode(interfaceName, methodName, new ArrayList<>());
        }
    }
    
    /**
     * Handle Spring Data Repository method
     */
    private void handleSpringDataRepositoryMethod(String interfaceName, String methodName, 
                                                ClassOrInterfaceDeclaration interfaceDeclaration,
                                                CallGraph callGraph) {
        System.out.println("Handling Spring Data Repository method: " + methodName);
        
        // For derived query methods like findByXyz
        if (methodName.startsWith("findBy") || methodName.startsWith("getBy") || 
            methodName.startsWith("queryBy") || methodName.startsWith("searchBy") ||
            methodName.startsWith("countBy") || methodName.startsWith("existsBy")) {
            
            // JPA executes the query directly to the database
            callGraph.addNode(interfaceName, methodName, new ArrayList<>());
            System.out.println("JPA Repository method: Executes direct database query");
            return;
        }
        
        // For CRUD operations from CrudRepository/JpaRepository
        if (methodName.equals("save") || methodName.equals("saveAll") || 
            methodName.equals("findById") || methodName.equals("findAll") || 
            methodName.equals("deleteById") || methodName.equals("delete") || 
            methodName.equals("count") || methodName.equals("existsById")) {
            
            callGraph.addNode(interfaceName, methodName, new ArrayList<>());
            System.out.println("JPA Repository method: Implemented by SimpleJpaRepository");
            return;
        }
        
        // Check for @Query annotation
        Optional<MethodDeclaration> methodOpt = interfaceDeclaration.findFirst(MethodDeclaration.class, 
                                                  m -> m.getNameAsString().equals(methodName));
        
        if (methodOpt.isPresent()) {
            MethodDeclaration method = methodOpt.get();
            boolean hasQueryAnnotation = method.getAnnotations().stream()
                                              .anyMatch(a -> a.getNameAsString().equals("Query"));
            
            if (hasQueryAnnotation) {
                callGraph.addNode(interfaceName, methodName, new ArrayList<>());
                System.out.println("JPA Repository method: Custom query with @Query annotation");
                return;
            }
        }
        
        // Default behavior for unrecognized methods
        callGraph.addNode(interfaceName, methodName, new ArrayList<>());
    }
    
    /**
     * Check if an interface is a Spring Data Repository
     */
    private boolean isSpringDataRepository(ClassOrInterfaceDeclaration interfaceDecl) {
        if (!interfaceDecl.isInterface()) {
            return false;
        }
        
        // Check if it extends any of the Spring Data repository interfaces
        return interfaceDecl.getExtendedTypes().stream()
            .anyMatch(type -> {
                String typeName = type.getNameAsString();
                return typeName.contains("Repository") || 
                       typeName.contains("JpaRepository") || 
                       typeName.contains("CrudRepository") || 
                       typeName.contains("PagingAndSortingRepository");
            });
    }
    
    /**
     * Find implementors of an interface in the codebase
     */
    private List<String> findImplementors(String interfaceName) {
        List<String> implementors = new ArrayList<>();
        String simpleInterfaceName = interfaceName.substring(interfaceName.lastIndexOf('.') + 1);
        
        for (String filePath : visitedFiles) {
            try {
                File file = new File(filePath);
                ParseResult<CompilationUnit> parseResult = javaParser.parse(file);
                
                if (parseResult.isSuccessful()) {
                    CompilationUnit cu = parseResult.getResult().get();
                    
                    // Find classes that implement this interface
                    List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class, 
                        c -> !c.isInterface() && c.getImplementedTypes().stream()
                            .anyMatch(t -> t.getNameAsString().equals(simpleInterfaceName)));
                    
                    for (ClassOrInterfaceDeclaration cls : classes) {
                        String packageName = cu.getPackageDeclaration()
                                              .map(pd -> pd.getNameAsString())
                                              .orElse("");
                        
                        implementors.add(packageName + "." + cls.getNameAsString());
                    }
                }
            } catch (FileNotFoundException e) {
                System.err.println("Error reading file: " + filePath);
            }
        }
        
        return implementors;
    }
    
    /**
     * Analyze fields in a class
     */
    private Map<String, String> analyzeFields(ClassOrInterfaceDeclaration classDecl, String className) {
        Map<String, String> fieldTypes = new HashMap<>();
        
        // Add "this" as a field of the current class type
        fieldTypes.put("this", className);
        
        // Check the imports in this class to help resolve types
        Map<String, String> importedTypes = new HashMap<>();
        if (classDecl.findCompilationUnit().isPresent()) {
            CompilationUnit cu = classDecl.findCompilationUnit().get();
            cu.getImports().forEach(importDecl -> {
                String importName = importDecl.getNameAsString();
                String simpleName = importName.substring(importName.lastIndexOf('.') + 1);
                importedTypes.put(simpleName, importName);
            });
        }
        
        // Analyze fields
        List<FieldDeclaration> fields = classDecl.findAll(FieldDeclaration.class);
        for (FieldDeclaration field : fields) {
            boolean isAutowired = field.getAnnotations().stream()
                                      .anyMatch(a -> a.getNameAsString().equals("Autowired"));
            
            for (VariableDeclarator var : field.getVariables()) {
                String fieldName = var.getNameAsString();
                String fieldType = var.getType().asString();
                
                // Skip primitive types
                if (!isPrimitiveOrCommonType(fieldType)) {
                    // First try to resolve from imports
                    String fullClassName = null;
                    if (importedTypes.containsKey(fieldType)) {
                        fullClassName = importedTypes.get(fieldType);
                    } else {
                        // Fallback to heuristic resolution
                        fullClassName = resolveClassName(fieldType, className);
                    }
                    
                    if (fullClassName != null) {
                        fieldTypes.put(fieldName, fullClassName);
                        if (isAutowired) {
                            System.out.println("Found autowired field: " + fieldName + " of type " + fullClassName);
                        } else {
                            System.out.println("Found field: " + fieldName + " of type " + fullClassName);
                        }
                    }
                }
            }
        }
        
        // Analyze constructor injected fields
        List<ConstructorDeclaration> constructors = classDecl.findAll(ConstructorDeclaration.class);
        for (ConstructorDeclaration constructor : constructors) {
            // Focus on public constructors
            if (constructor.isPublic()) {
                for (Parameter param : constructor.getParameters()) {
                    String paramName = param.getNameAsString();
                    String paramType = param.getType().asString();
                    
                    // Look for field assignments in the constructor
                    constructor.getBody().findAll(NameExpr.class, expr -> {
                        // Check if this is a field assignment (this.field = param)
                        if (expr.toString().equals("this." + paramName)) {
                            String fullClassName = resolveClassName(paramType, className);
                            if (fullClassName != null && !isPrimitiveOrCommonType(paramType)) {
                                fieldTypes.put(paramName, fullClassName);
                                System.out.println("Found constructor-injected field: " + 
                                                 paramName + " of type " + fullClassName);
                            }
                            return true;
                        }
                        return false;
                    });
                }
            }
        }
        
        return fieldTypes;
    }
    
    /**
     * Find method calls in a method
     */
    private List<MethodCall> findMethodCallsInMethod(MethodDeclaration method, String className) {
        List<MethodCall> calls = new ArrayList<>();
        Map<String, String> fieldTypes = classFields.getOrDefault(className, new HashMap<>());
        
        // Find all method calls in the method body
        method.findAll(MethodCallExpr.class).forEach(methodCall -> {
            try {
                // Skip common Java method calls
                String methodName = methodCall.getNameAsString();
                if (isCommonMethod(methodName)) {
                    return;
                }
                
                // Handle different types of method calls
                if (methodCall.getScope().isPresent()) {
                    Expression scope = methodCall.getScope().get();
                    
                    if (scope instanceof NameExpr) {
                        // It's an object method call: object.method()
                        String objectName = ((NameExpr) scope).getNameAsString();
                        String objectType = fieldTypes.get(objectName);
                        
                        if (objectType != null) {
                            calls.add(new MethodCall(objectName, objectType, methodName));
                        }
                    } else {
                        // Try to resolve method call target if possible
                        try {
                            ResolvedMethodDeclaration resolvedMethod = methodCall.resolve();
                            String declaringType = resolvedMethod.declaringType().getQualifiedName();
                            
                            // Add to calls if it's not a Java standard library class
                            if (!declaringType.startsWith("java.") && !declaringType.startsWith("javax.")) {
                                calls.add(new MethodCall(
                                    scope.toString(), declaringType, methodName));
                            }
                        } catch (Exception e) {
                            // Resolution failed, but we can still continue analysis
                        }
                    }
                } else {
                    // It's a method call within the same class (this.method() or just method())
                    calls.add(new MethodCall("this", className, methodName));
                }
            } catch (Exception e) {
                System.err.println("Error analyzing method call: " + methodCall);
            }
        });
        
        return calls;
    }
    
    /**
     * Resolve a class name to its fully qualified name
     */
    private String resolveClassName(String typeName, String currentClassName) {
        // Skip if already a full class name
        if (typeName.contains(".")) {
            return typeName;
        }
        
        // Skip primitive types and common Java types
        if (isPrimitiveOrCommonType(typeName)) {
            return null;
        }
        
        // Handle generics by extracting the base type
        if (typeName.contains("<")) {
            typeName = typeName.substring(0, typeName.indexOf('<'));
        }
        
        // Get the package of the current class
        String currentPackage = currentClassName.substring(0, currentClassName.lastIndexOf('.'));
        
        // Common package mappings for typical Spring components
        Map<String, String> packagePatterns = new HashMap<>();
        packagePatterns.put("Service", "service");
        packagePatterns.put("Repository", "dao");
        packagePatterns.put("Controller", "rest");
        packagePatterns.put("Entity", "entities");
        
        // Try to determine package based on naming conventions
        for (Map.Entry<String, String> entry : packagePatterns.entrySet()) {
            if (typeName.contains(entry.getKey())) {
                // Extract top-level package (preserving com.sbtl1.mod1 structure)
                // Find the last occurrence of '.service', '.rest', etc., and keep the module prefix
                int lastDotIndex = currentPackage.lastIndexOf('.');
                if (lastDotIndex > 0) {
                    String modulePrefix = currentPackage.substring(0, lastDotIndex);
                    // Check if we're in a module structure by looking for pattern com.xxx.moduleY
                    int secondLastDotIndex = modulePrefix.lastIndexOf('.');
                    if (secondLastDotIndex > 0) {
                        // Keep the full module prefix (e.g., com.sbtl1.mod1)
                        return modulePrefix + "." + entry.getValue() + "." + typeName;
                    }
                }
                
                // Fallback for non-module structure
                String basePackage = currentPackage.substring(0, 
                                   currentPackage.lastIndexOf('.', currentPackage.lastIndexOf('.') - 1));
                return basePackage + "." + entry.getValue() + "." + typeName;
            }
        }
        
        // Default to same package as current class
        return currentPackage + "." + typeName;
    }
    
    /**
     * Check if a type is primitive or a common Java type
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
     * Check if a method name is a common Java method
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
} 