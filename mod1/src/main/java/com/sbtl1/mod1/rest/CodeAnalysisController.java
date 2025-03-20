package com.sbtl1.mod1.rest;

import com.sbtl1.mod1.util.SimpleCodeFlowAnalyzer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/codeanalysis")
public class CodeAnalysisController {

    private final SimpleCodeFlowAnalyzer codeFlowAnalyzer;
    
    @Autowired
    public CodeAnalysisController(SimpleCodeFlowAnalyzer codeFlowAnalyzer) {
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
        
        SimpleCodeFlowAnalyzer.CallGraph callGraph = codeFlowAnalyzer.analyzeCallFlow(fullClassName, methodName);
        
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
     * Simplified call flow analysis for a specific endpoint
     * 
     * Example: /api/codeanalysis/endpoint/getUsersAboveAge
     */
    @GetMapping("/endpoint/{methodName}")
    public ResponseEntity<?> analyzeEndpoint(@PathVariable String methodName) {
        // Look in all controller classes for this method
        return analyzeCallFlow("rest", "UserController", methodName);
    }
    
    /**
     * Process the call graph into a more readable format
     */
    private Map<String, Object> processCallGraph(SimpleCodeFlowAnalyzer.CallGraph callGraph) {
        Map<String, Object> processedFlow = new HashMap<>();
        Map<String, List<String>> callHierarchy = new HashMap<>();
        
        // Build a textual representation of the call graph
        StringBuilder textGraph = new StringBuilder();
        callGraph.print(); // This will print to console
        
        // Generate a more structured representation
        for (String className : callGraph.getClasses()) {
            for (Map.Entry<String, List<SimpleCodeFlowAnalyzer.MethodCall>> entry : 
                    getMethodsForClass(callGraph, className).entrySet()) {
                
                String methodName = entry.getKey();
                List<SimpleCodeFlowAnalyzer.MethodCall> calls = entry.getValue();
                
                String methodSignature = className + "." + methodName;
                List<String> calleeSignatures = calls.stream()
                        .map(call -> call.className + "." + call.methodName)
                        .collect(Collectors.toList());
                
                callHierarchy.put(methodSignature, calleeSignatures);
            }
        }
        
        processedFlow.put("callHierarchy", callHierarchy);
        
        return processedFlow;
    }
    
    /**
     * Helper method to get all methods for a class from the call graph
     */
    private Map<String, List<SimpleCodeFlowAnalyzer.MethodCall>> getMethodsForClass(
            SimpleCodeFlowAnalyzer.CallGraph callGraph, String className) {
        
        Map<String, List<SimpleCodeFlowAnalyzer.MethodCall>> methods = new HashMap<>();
        
        // Get all methods in this class that are in the call graph
        // This is a simplified implementation since we don't have direct access to the internal map
        for (String methodName : getMethodsNames(callGraph, className)) {
            methods.put(methodName, callGraph.getCalls(className, methodName));
        }
        
        return methods;
    }
    
    /**
     * Helper method to get method names for a class from the call graph
     * This is a more dynamic implementation that works for any class in the call graph
     */
    private List<String> getMethodsNames(SimpleCodeFlowAnalyzer.CallGraph callGraph, String className) {
        // First check the starting class and method, which is always in the graph
        if (className.equals(callGraph.getStartClassName())) {
            return Collections.singletonList(callGraph.getStartMethodName());
        }
        
        // Then look for classes that are called by others in the graph
        Set<String> methodNames = new HashSet<>();
        for (String graphClass : callGraph.getClasses()) {
            // For each class in the graph, check if it makes calls to this class
            for (SimpleCodeFlowAnalyzer.MethodCall call : callGraph.getAllCalls()) {
                if (call.className.equals(className)) {
                    methodNames.add(call.methodName);
                }
            }
        }
        
        return new ArrayList<>(methodNames);
    }
} 