package com.sbtl1.mod1.util;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A command-line runner for analyzing code flow.
 * Only activated when the "codeanalysis" profile is active.
 * 
 * Usage:
 * ./gradlew bootRun --args="--spring.profiles.active=codeanalysis --analyze.class=com.sbtl1.mod1.rest.UserController --analyze.method=getUsersAboveAge"
 */
@Component
@Profile("codeanalysis")
public class CodeFlowAnalysisRunner implements CommandLineRunner {

    private final SimpleCodeFlowAnalyzer codeFlowAnalyzer;

    public CodeFlowAnalysisRunner(SimpleCodeFlowAnalyzer codeFlowAnalyzer) {
        this.codeFlowAnalyzer = codeFlowAnalyzer;
    }

    @Override
    public void run(String... args) {
        String className = System.getProperty("analyze.class");
        String methodName = System.getProperty("analyze.method");
        
        if (className == null || methodName == null) {
            System.out.println("Usage: -Danalyze.class=<fully.qualified.ClassName> -Danalyze.method=<methodName>");
            return;
        }
        
        System.out.println("Analyzing call flow for " + className + "." + methodName);
        
        SimpleCodeFlowAnalyzer.CallGraph callGraph = codeFlowAnalyzer.analyzeCallFlow(className, methodName);
        
        if (callGraph.getClasses().isEmpty()) {
            System.out.println("No call flow information found for the specified method.");
            return;
        }
        
        // Print classes involved
        System.out.println("\nClasses involved in the call flow:");
        callGraph.getClasses().stream()
                .sorted()
                .forEach(className1 -> System.out.println("- " + className1));
        
        // Print call graph
        System.out.println("\nCall Graph:");
        printCallGraph(className + "." + methodName, callGraph, 0, new HashSet<>());
    }
    
    private void printCallGraph(String methodSignature, SimpleCodeFlowAnalyzer.CallGraph callGraph, int depth, Set<String> visited) {
        // Prevent infinite recursion
        if (visited.contains(methodSignature)) {
            return;
        }
        visited.add(methodSignature);
        
        String indent = "  ".repeat(depth);
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