# Call Flow Analyzer for Spring Boot Applications

This project provides a simple, dependency-free way to analyze the call flow of methods in a Spring Boot application.

## Overview

The call flow analyzer helps in understanding how methods call each other within a Spring application. This is useful for:
- Understanding code flows
- Debugging
- Code reviews
- Documentation

## Features

- **No external dependencies required** - Works with just the JDK
- **Spring-aware** - Understands Spring Boot components like controllers, services, and repositories
- **Support for common patterns** - Handles constructor injection, field injection, and interfaces
- **JPA Repository support** - Identifies and explains JPA repository methods

## Components

1. `SimpleCodeFlowAnalyzer.java` - The main analyzer class that uses regex to analyze Java code
2. `SimpleCallFlowAnalysisDemo.java` - Demo application that runs the analyzer
3. `run-simple-analysis.sh` - Shell script to compile and run the analyzer

## How to Use

### 1. Run the analyzer with default settings:

```bash
./run-simple-analysis.sh
```

This will analyze the `getUsersAboveAge` method in the `UserController` by default.

### 2. Specify a different method to analyze:

```bash
./run-simple-analysis.sh com.example.package.SomeClassName someMethodName
```

### 3. Understanding the output:

The analyzer will print:
- Files being analyzed
- Fields and dependencies found in each class
- A tree representation of the call graph

## How It Works

The analyzer works by:

1. Reading Java source files
2. Using regex to find method definitions and dependencies
3. Tracking method calls within method bodies
4. Following the call chain recursively
5. Building and displaying a call graph

## Design Decisions

We used a regex-based approach rather than a full parser like JavaParser to:
- Eliminate external dependencies
- Make it easy to run on any Java version
- Keep the solution simple and maintainable

## Limitations

- Regex-based analysis is less precise than a full AST-based solution
- Complex code constructs might not be correctly analyzed
- Dynamic method calls (reflection, method references) are not supported
- Limited to analyzing source code available in the project

## Example Output

For the `getUsersAboveAge` method in `UserController`, the output shows:

```
Call Graph for com.sbtl1.mod1.rest.UserController.getUsersAboveAge:
com.sbtl1.mod1.rest.UserController.getUsersAboveAge
  com.sbtl1.mod1.service.UserService.getUsersByAge
    com.sbtl1.mod1.dao.UserRepository.findByAgeGreaterThan
```

## Future Improvements

- Add support for more Spring annotations and patterns
- Generate visual call graphs
- Add export to different formats (JSON, XML, GraphViz)
- Improve accuracy with a more sophisticated parsing approach

## Comparison with Other Solutions

1. **JavaParser-based solution**: 
   - More accurate but requires external dependencies
   - Harder to run standalone

2. **Dynamic analysis with AOP**:
   - Requires running the application
   - More accurate for runtime behavior

3. **This solution (regex-based)**:
   - No dependencies
   - Simple to run and understand
   - Works with source code only (no runtime needed) 