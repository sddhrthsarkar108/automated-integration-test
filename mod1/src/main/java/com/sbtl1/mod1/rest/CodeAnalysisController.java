package com.sbtl1.mod1.rest;

import com.sbtl1.mod1.util.JavaParserCodeFlowAnalyzer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/codeanalysis")
public class CodeAnalysisController {

    private final JavaParserCodeFlowAnalyzer codeFlowAnalyzer;
    
    @Autowired
    public CodeAnalysisController(JavaParserCodeFlowAnalyzer codeFlowAnalyzer) {
        this.codeFlowAnalyzer = codeFlowAnalyzer;
    }
    
    /**
     * Analyzes the call flow for a method in the specified class
     * 
     * Example: /api/codeanalysis/flow/rest.UserController/getUsersAboveAge
     */
    @GetMapping("/flow/{packagePath}/{className}/{methodName}")
    public ResponseEntity<?> analyzeCallFlow(
            @PathVariable String packagePath,
            @PathVariable String className,
            @PathVariable String methodName) {
        
        String fullClassName = "com.sbtl1.mod1." + (packagePath.isEmpty() ? "" : packagePath + ".") + className;
        
        // Use JavaParserCodeFlowAnalyzer
        JavaParserCodeFlowAnalyzer.CallGraph callGraph = codeFlowAnalyzer.analyzeCallFlow(fullClassName, methodName);
        
        if (callGraph == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("startClass", fullClassName);
        result.put("startMethod", methodName);
        
        // Process the call graph for a more readable output
        Map<String, Object> callGraphResult = processCallGraph(callGraph);
        result.put("callGraph", callGraphResult);
        
        // Extract the list of files involved
        Set<String> filesInvolved = callGraph.getClasses();
        result.put("filesInvolved", filesInvolved);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Analyzes the call flow for an endpoint (controller method)
     * 
     * Example: /api/codeanalysis/endpoint/getUsersAboveAge
     */
    @GetMapping("/endpoint/{methodName}")
    public ResponseEntity<?> analyzeEndpoint(@PathVariable String methodName) {
        // For simplicity, we'll just look for the method in UserController
        String controllerClassName = "com.sbtl1.mod1.rest.UserController";
        
        // Use JavaParserCodeFlowAnalyzer
        JavaParserCodeFlowAnalyzer.CallGraph callGraph = codeFlowAnalyzer.analyzeCallFlow(controllerClassName, methodName);
        
        if (callGraph == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("startClass", controllerClassName);
        result.put("startMethod", methodName);
        
        // Process the call graph for a more readable output
        Map<String, Object> callGraphResult = processCallGraph(callGraph);
        result.put("callGraph", callGraphResult);
        
        // Extract the list of files involved
        Set<String> filesInvolved = callGraph.getClasses();
        result.put("filesInvolved", filesInvolved);
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Simplified call flow analysis for a specific endpoint with specified controller
     * 
     * Example: /api/codeanalysis/endpoint/UserController/getUsersAboveAge
     */
    @GetMapping("/endpoint/{controllerName}/{methodName}")
    public ResponseEntity<?> analyzeEndpointWithController(
            @PathVariable String controllerName, 
            @PathVariable String methodName) {
        return analyzeCallFlow("rest", controllerName, methodName);
    }
    
    /**
     * Retrieves code snippets for all methods in the execution path
     * Formats the output for use in LLM prompts
     * 
     * Example: /api/codeanalysis/snippets/rest/UserController/getUsersAboveAge
     */
    @GetMapping("/snippets/{packagePath}/{className}/{methodName}")
    public ResponseEntity<String> getCodeSnippets(
            @PathVariable String packagePath,
            @PathVariable String className,
            @PathVariable String methodName) {
        
        String fullClassName = "com.sbtl1.mod1." + (packagePath.isEmpty() ? "" : packagePath + ".") + className;
        
        // Use JavaParserCodeFlowAnalyzer
        JavaParserCodeFlowAnalyzer.CallGraph callGraph = codeFlowAnalyzer.analyzeCallFlow(fullClassName, methodName);
        
        if (callGraph == null) {
            return ResponseEntity.notFound().build();
        }
        
        StringBuilder snippets = new StringBuilder();
        snippets.append("# Code Execution Path Analysis\n\n");
        snippets.append("## Call Graph Overview\n\n");
        snippets.append("```\n");
        
        // Add call graph structure in text format
        appendCallHierarchy(snippets, callGraph, fullClassName, methodName, 0, new HashSet<>());
        
        snippets.append("```\n\n");
        snippets.append("## Method Code Snippets\n\n");
        
        // Add entity classes referenced in the repositories
        Map<String, Set<String>> classMethodMap = collectClassMethods(callGraph);
        Set<String> processedClasses = new HashSet<>();
        Set<String> repoClassNames = new HashSet<>();
        
        // Get entity class names from repository methods
        Set<String> entityClasses = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : classMethodMap.entrySet()) {
            String cls = entry.getKey();
            if (cls.contains(".dao.") && cls.endsWith("Repository")) {
                repoClassNames.add(cls); // Keep track of repository classes
                // Try to find the entity class directly from the file
                try {
                    String filePath = findRepositoryFile(cls);
                    if (filePath != null) {
                        String content = new String(Files.readAllBytes(Paths.get(filePath)));
                        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                                "JpaRepository\\s*<\\s*(\\w+)\\s*,");
                        java.util.regex.Matcher matcher = pattern.matcher(content);
                        if (matcher.find()) {
                            String entityName = matcher.group(1);
                            // Add the entity class
                            String entityClass = "com.sbtl1.mod1.entities." + entityName;
                            entityClasses.add(entityClass);
                        }
                    }
                } catch (Exception e) {
                    snippets.append("<!-- Error finding entity: " + e.getMessage() + " -->\n");
                }
            }
        }
        
        // Add entity classes to the method map
        for (String entityClass : entityClasses) {
            Set<String> methods = classMethodMap.computeIfAbsent(entityClass, k -> new HashSet<>());
            methods.add("__entity__"); // Special marker for entity class
        }
        
        // Add code snippets for each method
        for (Map.Entry<String, Set<String>> entry : classMethodMap.entrySet()) {
            String cls = entry.getKey();
            
            // Add class-level overview only once
            if (!processedClasses.contains(cls)) {
                processedClasses.add(cls);
                
                // Add class overview section based on type
                if (cls.contains(".entities.")) {
                    snippets.append("### Entity: ").append(cls).append("\n\n");
                    
                    // Add the entity class code
                    try {
                        String entityCode = getEntityClassCode(cls);
                        snippets.append("```java\n");
                        snippets.append(entityCode).append("\n");
                        snippets.append("```\n\n");
                    } catch (Exception e) {
                        snippets.append("<!-- Error reading entity: " + e.getMessage() + " -->\n");
                    }
                    
                } else if (cls.contains(".dao.")) {
                    snippets.append("### Repository: ").append(cls).append("\n\n");
                    
                    // Add the repository interface code
                    try {
                        String repoCode = getRepositoryCode(cls);
                        snippets.append("```java\n");
                        snippets.append(repoCode).append("\n");
                        snippets.append("```\n\n");
                        
                        // Add explanation of Spring Data JPA methods
                        snippets.append("Spring Data JPA repositories automatically implement methods based on naming conventions. " +
                                        "For example, `findByAgeGreaterThan(int age)` is translated into a SQL query: " +
                                        "`SELECT * FROM users WHERE age > ?`\n\n");
                        
                    } catch (Exception e) {
                        snippets.append("<!-- Error reading repository: " + e.getMessage() + " -->\n");
                    }
                    
                } else if (cls.contains(".service.")) {
                    snippets.append("### Service: ").append(cls).append("\n\n");
                } else if (cls.contains(".rest.")) {
                    snippets.append("### Controller: ").append(cls).append("\n\n");
                } else {
                    snippets.append("### Class: ").append(cls).append("\n\n");
                }
            }
            
            // Process methods for the class (skip for entity classes and repository methods already shown)
            if (!cls.contains(".entities.") && !isRepositoryMethod(cls, entry.getValue(), repoClassNames)) {
                for (String mtd : entry.getValue()) {
                    if (mtd.equals("__entity__")) {
                        continue; // Skip placeholder method for entities
                    }
                    
                    String sourceCode = extractMethodSource(cls, mtd);
                    if (sourceCode != null && !sourceCode.startsWith("// Source file not found")) {
                        snippets.append("#### ").append(mtd).append("\n\n");
                        snippets.append("```java\n");
                        snippets.append(sourceCode).append("\n");
                        snippets.append("```\n\n");
                    }
                }
            }
        }
        
        snippets.append("## End of Analysis\n");
        
        return ResponseEntity.ok(snippets.toString());
    }
    
    /**
     * Simplified snippets endpoint for a specific controller method
     * 
     * Example: /api/codeanalysis/snippets/endpoint/getUsersAboveAge
     */
    @GetMapping("/snippets/endpoint/{methodName}")
    public ResponseEntity<String> getEndpointSnippets(@PathVariable String methodName) {
        // Try to find the controller containing this method
        String controllerName = findControllerForMethod(methodName);
        if (controllerName != null) {
            return getCodeSnippets("rest", controllerName, methodName);
        }
        
        // Fall back to UserController if we couldn't find the method elsewhere
        return getCodeSnippets("rest", "UserController", methodName);
    }
    
    /**
     * Simplified snippets endpoint with specified controller
     * 
     * Example: /api/codeanalysis/snippets/endpoint/UserController/getUsersAboveAge
     */
    @GetMapping("/snippets/endpoint/{controllerName}/{methodName}")
    public ResponseEntity<String> getEndpointSnippetsWithController(
            @PathVariable String controllerName,
            @PathVariable String methodName) {
        return getCodeSnippets("rest", controllerName, methodName);
    }
    
    /**
     * Recursively appends call hierarchy to the string builder
     */
    private void appendCallHierarchy(StringBuilder sb, JavaParserCodeFlowAnalyzer.CallGraph callGraph, 
                                    String className, String methodName, int depth, Set<String> visited) {
        String signature = className + "." + methodName;
        if (visited.contains(signature)) {
            appendIndent(sb, depth);
            sb.append(signature).append(" (recursive call)\n");
            return;
        }
        
        visited.add(signature);
        
        appendIndent(sb, depth);
        sb.append(signature).append("\n");
        
        List<JavaParserCodeFlowAnalyzer.MethodCall> calls = callGraph.getCalls(className, methodName);
        if (calls != null) {
            for (JavaParserCodeFlowAnalyzer.MethodCall call : calls) {
                appendCallHierarchy(sb, callGraph, call.className, call.methodName, depth + 1, visited);
            }
        }
    }
    
    /**
     * Appends indentation to the string builder
     */
    private void appendIndent(StringBuilder sb, int depth) {
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
    }
    
    /**
     * Collects all classes and their methods from the call graph
     */
    private Map<String, Set<String>> collectClassMethods(JavaParserCodeFlowAnalyzer.CallGraph callGraph) {
        Map<String, Set<String>> classMethodMap = new HashMap<>();
        
        // Process each class in the graph
        for (String className : callGraph.getClasses()) {
            Set<String> methods = classMethodMap.computeIfAbsent(className, k -> new HashSet<>());
            methods.addAll(callGraph.getMethods(className));
            
            // Find methods in this class that are called
            for (JavaParserCodeFlowAnalyzer.MethodCall call : callGraph.getAllCalls()) {
                if (call.className.equals(className)) {
                    methods.add(call.methodName);
                }
            }
        }
        return classMethodMap;
    }
    
    /**
     * Extracts source code for a specific method from its class file
     */
    private String extractMethodSource(String className, String methodName) {
        try {
            // Convert class name to file path
            String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
            String packageName = className.substring(0, className.lastIndexOf('.'));
            String packagePath = packageName.replace('.', File.separatorChar);
            
            // Get the source root path from the analyzer
            String sourceRootPath = new File(System.getProperty("user.dir")).getAbsolutePath();
            
            // Find the actual file by trying common source paths
            String[] possiblePaths = {
                Paths.get(sourceRootPath, "mod1", "src", "main", "java", packagePath, simpleClassName + ".java").toString(),
                Paths.get(sourceRootPath, "src", "main", "java", packagePath, simpleClassName + ".java").toString()
            };
            
            String filePath = null;
            for (String path : possiblePaths) {
                if (Files.exists(Paths.get(path))) {
                    filePath = path;
                    break;
                }
            }
            
            if (filePath == null) {
                // Special handling for Spring Data JPA repository methods
                if (className.endsWith("Repository") && className.contains(".dao.")) {
                    // Try to extract interface declaration and method signature
                    for (String path : possiblePaths) {
                        String possibleFilePath = path.replace(".java", ".java");
                        if (Files.exists(Paths.get(possibleFilePath))) {
                            String content = new String(Files.readAllBytes(Paths.get(possibleFilePath)));
                            
                            // Extract the interface declaration including the method
                            java.util.regex.Pattern interfacePattern = java.util.regex.Pattern.compile(
                                "public\\s+interface\\s+" + simpleClassName + ".*\\{([^}]*)" + 
                                java.util.regex.Pattern.quote(methodName) + "[^;]*;", 
                                java.util.regex.Pattern.DOTALL);
                            
                            java.util.regex.Matcher interfaceMatcher = interfacePattern.matcher(content);
                            if (interfaceMatcher.find()) {
                                // Find the specific method
                                java.util.regex.Pattern methodPattern = java.util.regex.Pattern.compile(
                                    "\\s*(?:.*)(List|Set|Collection|Optional|\\w+)<[^>]*>\\s+" + 
                                    java.util.regex.Pattern.quote(methodName) + "\\s*\\([^)]*\\)\\s*;", 
                                    java.util.regex.Pattern.DOTALL);
                                
                                java.util.regex.Matcher methodMatcher = methodPattern.matcher(content);
                                if (methodMatcher.find()) {
                                    return "// Spring Data JPA Repository Interface Method\n" + 
                                           "@Repository\npublic interface " + simpleClassName + " extends JpaRepository<...> {\n    " + 
                                           methodMatcher.group(0).trim() + "\n    // This method is implemented automatically by Spring Data JPA\n}";
                                }
                            }
                        }
                    }
                    
                    // If we couldn't extract it, provide a generic explanation
                    return "// Method " + methodName + " is a Spring Data JPA repository method\n" +
                           "// It is automatically implemented by Spring based on the method name pattern\n" +
                           "// For example: findByAgeGreaterThan generates a query like:\n" +
                           "// SELECT * FROM users WHERE age > ?";
                }
                
                return "// Source file not found for " + className;
            }
            
            // Read file content
            String fileContent = new String(Files.readAllBytes(Paths.get(filePath)));
            
            // Check if this is an entity class referenced in the call chain
            boolean isEntity = fileContent.contains("@Entity") && className.contains(".entities.");
            if (isEntity && !"toString".equals(methodName) && !"hashCode".equals(methodName) && !"equals".equals(methodName)) {
                // For entity classes, include the whole class as context
                java.util.regex.Pattern classPattern = java.util.regex.Pattern.compile(
                        "(?:@Entity.*class|class)\\s+" + simpleClassName + "\\s+(?:extends\\s+\\w+\\s+)?(?:implements\\s+[\\w,\\s]+\\s+)?\\{([^}]*)\\}", 
                        java.util.regex.Pattern.DOTALL);
                
                java.util.regex.Matcher classMatcher = classPattern.matcher(fileContent);
                if (classMatcher.find()) {
                    return "// Entity class used in the data access layer\n" + 
                           fileContent.substring(classMatcher.start(), classMatcher.end());
                }
            }
            
            // Extract method code using regex
            // Find the method in the file - handle methods with access modifiers or default methods
            java.util.regex.Pattern methodPattern = java.util.regex.Pattern.compile(
                    "(?:public|private|protected|default|)\\s+(?:static\\s+)?(?:[\\w<>\\[\\],\\s]+)\\s+" + 
                    java.util.regex.Pattern.quote(methodName.replace("default ", "")) + "\\s*\\([^)]*\\)\\s*(?:throws[^{]+)?\\{", 
                    java.util.regex.Pattern.DOTALL);
            
            java.util.regex.Matcher methodMatcher = methodPattern.matcher(fileContent);
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
                    return fileContent.substring(startPos, pos).trim();
                }
            }
            
            return "// Method " + methodName + " not found in " + className;
        } catch (IOException e) {
            return "// Error extracting source: " + e.getMessage();
        }
    }
    
    /**
     * Process the call graph into a more readable format
     */
    private Map<String, Object> processCallGraph(JavaParserCodeFlowAnalyzer.CallGraph callGraph) {
        Map<String, Object> processedFlow = new HashMap<>();
        Map<String, List<String>> callHierarchy = new HashMap<>();
        
        // Debug the call graph structure if needed
        // debugCallGraph(callGraph);
        
        // Generate a more structured representation
        for (String className : callGraph.getClasses()) {
            for (Map.Entry<String, List<JavaParserCodeFlowAnalyzer.MethodCall>> entry : 
                    getMethodsForClass(callGraph, className).entrySet()) {
                
                String methodName = entry.getKey();
                List<JavaParserCodeFlowAnalyzer.MethodCall> calls = entry.getValue();
                
                String methodSignature = className + "." + methodName;
                callHierarchy.put(methodSignature, 
                        calls.stream().map(c -> c.className + "." + c.methodName).collect(Collectors.toList()));
            }
        }
        
        processedFlow.put("callHierarchy", callHierarchy);
        
        return processedFlow;
    }
    
    /**
     * Helper method to get all methods for a class from the call graph
     */
    private Map<String, List<JavaParserCodeFlowAnalyzer.MethodCall>> getMethodsForClass(
            JavaParserCodeFlowAnalyzer.CallGraph callGraph, String className) {
        
        Map<String, List<JavaParserCodeFlowAnalyzer.MethodCall>> methods = new HashMap<>();
        
        // Get all methods in this class that are in the call graph
        // This is a simplified implementation since we don't have direct access to the internal map
        for (String methodName : getMethodsNames(callGraph, className)) {
            methods.put(methodName, callGraph.getCalls(className, methodName));
        }
        
        return methods;
    }
    
    /**
     * This is a more dynamic implementation that works for any class in the call graph
     */
    private List<String> getMethodsNames(JavaParserCodeFlowAnalyzer.CallGraph callGraph, String className) {
        Set<String> methodNames = new HashSet<>();
        
        // Get methods directly from the call map
        if (callGraph.getClasses().contains(className)) {
            methodNames.addAll(callGraph.getMethods(className));
        }
        
        // Also find any methods in this class that are called by other classes
        for (JavaParserCodeFlowAnalyzer.MethodCall call : callGraph.getAllCalls()) {
            if (call.className.equals(className)) {
                methodNames.add(call.methodName);
            }
        }
        
        return new ArrayList<>(methodNames);
    }
    
    /**
     * Debug method to print the call graph structure
     */
    private void debugCallGraph(JavaParserCodeFlowAnalyzer.CallGraph callGraph) {
        System.out.println("CALL GRAPH DEBUG:");
        System.out.println("Classes: " + callGraph.getClasses());
        
        for (String className : callGraph.getClasses()) {
            System.out.println("Class: " + className);
            Set<String> methods = callGraph.getMethods(className);
            System.out.println("  Methods: " + methods);
            
            for (String methodName : methods) {
                List<JavaParserCodeFlowAnalyzer.MethodCall> calls = callGraph.getCalls(className, methodName);
                System.out.println("  " + methodName + " calls: " + calls);
            }
        }
    }
    
    /**
     * Find repository file by class name
     */
    private String findRepositoryFile(String className) {
        try {
            String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
            String packageName = className.substring(0, className.lastIndexOf('.'));
            String packagePath = packageName.replace('.', File.separatorChar);
            
            String sourceRootPath = new File(System.getProperty("user.dir")).getAbsolutePath();
            
            String[] possiblePaths = {
                Paths.get(sourceRootPath, "mod1", "src", "main", "java", packagePath, simpleClassName + ".java").toString(),
                Paths.get(sourceRootPath, "src", "main", "java", packagePath, simpleClassName + ".java").toString()
            };
            
            for (String path : possiblePaths) {
                if (Files.exists(Paths.get(path))) {
                    return path;
                }
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get the complete entity class code
     */
    private String getEntityClassCode(String className) throws IOException {
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        String packageName = className.substring(0, className.lastIndexOf('.'));
        String packagePath = packageName.replace('.', File.separatorChar);
        
        String sourceRootPath = new File(System.getProperty("user.dir")).getAbsolutePath();
        
        String[] possiblePaths = {
            Paths.get(sourceRootPath, "mod1", "src", "main", "java", packagePath, simpleClassName + ".java").toString(),
            Paths.get(sourceRootPath, "src", "main", "java", packagePath, simpleClassName + ".java").toString()
        };
        
        for (String path : possiblePaths) {
            if (Files.exists(Paths.get(path))) {
                return new String(Files.readAllBytes(Paths.get(path)));
            }
        }
        
        return "// Entity class not found: " + className;
    }
    
    /**
     * Get the complete repository interface code
     */
    private String getRepositoryCode(String className) throws IOException {
        String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
        String packageName = className.substring(0, className.lastIndexOf('.'));
        String packagePath = packageName.replace('.', File.separatorChar);
        
        String sourceRootPath = new File(System.getProperty("user.dir")).getAbsolutePath();
        
        String[] possiblePaths = {
            Paths.get(sourceRootPath, "mod1", "src", "main", "java", packagePath, simpleClassName + ".java").toString(),
            Paths.get(sourceRootPath, "src", "main", "java", packagePath, simpleClassName + ".java").toString()
        };
        
        for (String path : possiblePaths) {
            if (Files.exists(Paths.get(path))) {
                return new String(Files.readAllBytes(Paths.get(path)));
            }
        }
        
        return "// Repository interface not found: " + className;
    }
    
    /**
     * Check if the method is a repository method that was already displayed in the repository interface
     */
    private boolean isRepositoryMethod(String className, Set<String> methods, Set<String> repoClassNames) {
        if (className.contains(".dao.") && className.endsWith("Repository")) {
            return true; // This is a repository class we've already shown
        }
        return false;
    }
    
    /**
     * Find the controller class that contains a given method
     */
    private String findControllerForMethod(String methodName) {
        try {
            // Get the source root path
            String sourceRootPath = new File(System.getProperty("user.dir")).getAbsolutePath();
            String controllersPath = Paths.get(sourceRootPath, "mod1", "src", "main", "java", 
                                             "com", "sbtl1", "mod1", "rest").toString();
            
            File controllersDir = new File(controllersPath);
            if (!controllersDir.exists() || !controllersDir.isDirectory()) {
                return null;
            }
            
            // Scan all Java files in the controllers directory
            File[] files = controllersDir.listFiles((dir, name) -> name.endsWith("Controller.java"));
            if (files == null) {
                return null;
            }
            
            // Check each controller file for the method
            for (File file : files) {
                String content = new String(Files.readAllBytes(file.toPath()));
                
                // Simple regex to check for method definition
                java.util.regex.Pattern methodPattern = java.util.regex.Pattern.compile(
                        "(?:public|private|protected)\\s+(?:static\\s+)?(?:[\\w<>\\[\\],\\s]+)\\s+" + 
                        java.util.regex.Pattern.quote(methodName) + "\\s*\\([^)]*\\)", 
                        java.util.regex.Pattern.DOTALL);
                
                java.util.regex.Matcher matcher = methodPattern.matcher(content);
                if (matcher.find()) {
                    // Found the method in this controller
                    return file.getName().replace(".java", "");
                }
            }
            
            return null;
        } catch (Exception e) {
            return null;
        }
    }
} 