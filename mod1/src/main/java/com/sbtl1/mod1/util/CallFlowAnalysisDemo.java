package com.sbtl1.mod1.util;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A simple standalone demo for the regex-based simple code flow analysis.
 */
public class CallFlowAnalysisDemo {
    
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java CallFlowAnalysisDemo <className> <methodName>");
            System.out.println("Example: java CallFlowAnalysisDemo com.sbtl1.mod1.rest.UserController getUsersAboveAge");
            return;
        }
        
        String className = args[0];
        String methodName = args[1];
        
        try {
            System.out.println("Analyzing call flow for " + className + "." + methodName + "...\n");
            
            // Create the analyzer
            SimpleCodeFlowAnalyzer analyzer = new SimpleCodeFlowAnalyzer();
            
            // Analyze the call flow
            SimpleCodeFlowAnalyzer.CallGraph callGraph = analyzer.analyzeCallFlow(className, methodName);
            
            // Print files involved
            System.out.println("\nClasses involved in the call flow:");
            for (String classInvolved : callGraph.getClasses()) {
                System.out.println("- " + classInvolved);
            }
            
            // Print call graph
            System.out.println("\nCall Graph:");
            printCallGraph(className + "." + methodName, callGraph, 0, new HashSet<>());
            
        } catch (Exception e) {
            System.err.println("Error analyzing call flow: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void printCallGraph(String methodSignature, SimpleCodeFlowAnalyzer.CallGraph callGraph, int depth, Set<String> visited) {
        // Prevent infinite recursion
        if (visited.contains(methodSignature)) {
            return;
        }
        visited.add(methodSignature);
        
        // Create indentation
        StringBuilder indent = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indent.append("  ");
        }
        
        System.out.println(indent + methodSignature);
        
        // Extract class and method name
        String className = methodSignature.substring(0, methodSignature.lastIndexOf('.'));
        String methodName = methodSignature.substring(methodSignature.lastIndexOf('.') + 1);
        
        // Get calls for this method
        List<SimpleCodeFlowAnalyzer.MethodCall> methodCalls = callGraph.getCalls(className, methodName);
        if (methodCalls == null || methodCalls.isEmpty()) {
            return;
        }
        
        // For each method call
        for (SimpleCodeFlowAnalyzer.MethodCall call : methodCalls) {
            String calleeSignature = call.className + "." + call.methodName;
            printCallGraph(calleeSignature, callGraph, depth + 1, visited);
        }
    }
} 