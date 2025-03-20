package com.sbtl1.mod1.util;

/**
 * A simplified demo of the call flow analysis using our custom SimpleCodeFlowAnalyzer.
 * This avoids the JavaParser dependency completely.
 */
public class SimpleCallFlowAnalysisDemo {
    
    public static void main(String[] args) {
        String className = "com.sbtl1.mod1.rest.UserController";
        String methodName = "getUsersAboveAge";
        
        // Override with command line arguments if provided
        if (args.length >= 2) {
            className = args[0];
            methodName = args[1];
        }
        
        System.out.println("Starting call flow analysis for " + className + "." + methodName);
        SimpleCodeFlowAnalyzer analyzer = new SimpleCodeFlowAnalyzer();
        SimpleCodeFlowAnalyzer.CallGraph callGraph = analyzer.analyzeCallFlow(className, methodName);
        
        // Print the results
        callGraph.print();
    }
} 