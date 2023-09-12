Table of Contents
=================

   * [General info](#general-info)
   * [Requirements](#requirements)
   * [API usage guidelines](#api-usage-guidelines)
   * [Location information](#location-information)
   * [How to build and run](#how-to-build-and-run)
     * [Command line](#command-line)
     * [IntelliJ IDEA](#intellij-idea)


# General info
ReMapper is a library/API written in Java that can match code entities between two successive versions of a Java project.

Currently, it supports the matching of the following code entities:

**<ins>supported by ReMapper 1.0 and newer versions</ins>**

1. Methods
2. Fields
3. Classes
4. Interfaces
5. Enums
6. @interface (Annotation Type Declarations)
7. Initializers
8. Enum Constants
9. Annotation Members

**<ins>supported by ReMapper 1.1 and newer versions</ins>**

10. Assert Statements
11. Blocks
12. Break Statements
13. Constructor Invocations
14. Continue Statements
15. Do Statements
16. Empty Statements
17. Enhanced For Statements
18. Expression Statements
19. For Statements
20. If Statements
21. Labeled Statements
22. Return Statements
23. Super Constructor Invocations
24. Switch Cases
25. Switch Statements
26. Synchronized Statements
27. Throw Statements
28. Try Statements
29. Type Declaration Statements
30. Variable Declaration Statements
31. While Statements
32. Catch Clauses

# Requirements

Java 11.0.17 or newer

Apache Maven 3.8.1 or newer

# API usage guidelines

ReMapper can automatically match code entities between two successive versions of a git repository.

In the code snippet below we demonstrate how to print all matched code entities performed at a specific commit in the toy project https://github.com/danilofes/refactoring-toy-example.git. The commit is identified by its SHA key, such as in the example below:

```java
GitService gitService = new GitServiceImpl();
try (Repository repo = gitService.openRepository("E:/refactoring-toy-example")) {
  EntityMatcherService matcher = new EntityMatcherServiceImpl();
  matcher.matchAtCommit(repo, "d4bce13a443cf12da40a77c16c1e591f4f985b47", new RefactoringHandler() {
    @Override
    public void handle(String commitId, MatchPair matchPair) {
      System.out.println("Matched code entities at " + commitId);
      for (Pair<EntityInfo, EntityInfo> pair : matchPair.getMatchedEntityInfos()) {
        System.out.println("Old entity --> " + pair.getLeft());
        System.out.println("New entity --> " + pair.getRight());
        System.out.println();
      }
    }
      
    @Override
    public void handleException(String commit, Exception e) {
      System.err.println("Error processing commit " + commit);
      e.printStackTrace(System.err);
    }
  });
}
```

# Location information
Each code entity offers the `LocationInfo getLocation()` method to return a `LocationInfo` object including the following properties:

```java
String filePath
int startLine
int endLine
int startColumn
int endColumn
```

# How to build and run

## Command line

1. **Clone repository**

   `git clone https://github.com/lyoubo/ReMapper.git`

2. **Cd  in the locally cloned repository folder**

   `cd ReMapper`

3. **Build ReMapper**

   `mvn install`

4. **Run the API usage example shown in README**

   `mvn compile exec:java -Dexec.mainClass="org.remapper.ReMapper" -Dexec.args="-c refactoring-toy-example d4bce13a443cf12da40a77c16c1e591f4f985b47"`

## IntelliJ IDEA

1. **Clone repository**

   `git clone https://github.com/lyoubo/ReMapper.git`

2. **Import project**

   Go to *File* -> *Open...*

   Browse to the root directory of project ReMapper

   Click *OK*

   The project will be built automatically.

3. **Run the API usage examples shown in README**

   From the Project tab navigate to `org.remapper.ReMapper`

   Right-click on the file and select *Run ReMapper.main()*

**You can add the `-json <path-to-json-file>` command arguments to save the JSON output in a file. The results are appended to the file after each processed commit.**

In both cases, you will get the output in JSON format:

    {
      "results": [
        {
          "repository": "https://github.com/danilofes/refactoring-toy-example.git",
          "sha1": "d4bce13a443cf12da40a77c16c1e591f4f985b47",
          "url": "https://github.com/danilofes/refactoring-toy-example/commit/d4bce13a443cf12da40a77c16c1e591f4f985b47",
          "matchedEntities": [
            {
              "leftSideLocation": {
                "container": "org.DogManager",
                "type": "Method",
                "name": "doStuff",
                "filePath": "src/org/DogManager.java",
                "startLine": 12,
                "endLine": 23,
                "startColumn": 2,
                "endColumn": 3
              },
              "rightSideLocation": {
                "container": "org.DogManager",
                "type": "Method",
                "name": "doStuff",
                "filePath": "src/org/DogManager.java",
                "startLine": 12,
                "endLine": 23,
                "startColumn": 2,
                "endColumn": 3
              }
            },
            {
              "leftSideLocation": {
                "container": "org.DogManager",
                "type": "Class",
                "name": "DogManager",
                "filePath": "src/org/DogManager.java",
                "startLine": 5,
                "endLine": 25,
                "startColumn": 0,
                "endColumn": 2
              },
              "rightSideLocation": {
                "container": "org.DogManager",
                "type": "Class",
                "name": "DogManager",
                "filePath": "src/org/DogManager.java",
                "startLine": 5,
                "endLine": 34,
                "startColumn": 0,
                "endColumn": 2
              }
            },
            {
              "leftSideLocation": {
                "container": "org.animals.Dog",
                "type": "Class",
                "name": "Dog",
                "filePath": "src/org/animals/Dog.java",
                "startLine": 5,
                "endLine": 27,
                "startColumn": 0,
                "endColumn": 2
              },
              "rightSideLocation": {
                "container": "org.animals.Dog",
                "type": "Class",
                "name": "Dog",
                "filePath": "src/org/animals/Dog.java",
                "startLine": 4,
                "endLine": 17,
                "startColumn": 0,
                "endColumn": 2
              }
            },
            {
              "leftSideLocation": {
                "container": "org.animals.Dog",
                "type": "Method",
                "name": "barkBark",
                "filePath": "src/org/animals/Dog.java",
                "startLine": 14,
                "endLine": 21,
                "startColumn": 2,
                "endColumn": 3
              },
              "rightSideLocation": {
                "container": "org.DogManager",
                "type": "Method",
                "name": "barkBark",
                "filePath": "src/org/DogManager.java",
                "startLine": 25,
                "endLine": 32,
                "startColumn": 2,
                "endColumn": 3
              }
            },
            {
              "leftSideLocation": {
                "method": "public doStuff() : void",
                "type": "For Statement",
                "expression": "for(int i=0; i < age; i++)",
                "filePath": "src/org/DogManager.java",
                "startLine": 17,
                "endLine": 20,
                "startColumn": 3,
                "endColumn": 4
              },
              "rightSideLocation": {
                "method": "public doStuff() : void",
                "type": "For Statement",
                "expression": "for(int i=0; i < age; i++)",
                "filePath": "src/org/DogManager.java",
                "startLine": 17,
                "endLine": 20,
                "startColumn": 3,
                "endColumn": 4
              }
            },
            {
              "leftSideLocation": {
                "method": "public doStuff() : void",
                "type": "Block",
                "expression": "{",
                "filePath": "src/org/DogManager.java",
                "startLine": 17,
                "endLine": 20,
                "startColumn": 33,
                "endColumn": 4
              },
              "rightSideLocation": {
                "method": "public doStuff() : void",
                "type": "Block",
                "expression": "{",
                "filePath": "src/org/DogManager.java",
                "startLine": 17,
                "endLine": 20,
                "startColumn": 33,
                "endColumn": 4
              }
            },
            {
              "leftSideLocation": {
                "method": "public doStuff() : void",
                "type": "Expression Statement",
                "expression": "System.out.println(i);\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 18,
                "endLine": 18,
                "startColumn": 4,
                "endColumn": 26
              },
              "rightSideLocation": {
                "method": "public doStuff() : void",
                "type": "Expression Statement",
                "expression": "System.out.println(i);\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 18,
                "endLine": 18,
                "startColumn": 4,
                "endColumn": 26
              }
            },
            {
              "leftSideLocation": {
                "method": "public doStuff() : void",
                "type": "Variable Declaration Statement",
                "expression": "int sum=0;\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 16,
                "endLine": 16,
                "startColumn": 3,
                "endColumn": 15
              },
              "rightSideLocation": {
                "method": "public doStuff() : void",
                "type": "Variable Declaration Statement",
                "expression": "int sum=0;\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 16,
                "endLine": 16,
                "startColumn": 3,
                "endColumn": 15
              }
            },
            {
              "leftSideLocation": {
                "method": "public doStuff() : void",
                "type": "Expression Statement",
                "expression": "sum+=i;\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 19,
                "endLine": 19,
                "startColumn": 4,
                "endColumn": 13
              },
              "rightSideLocation": {
                "method": "public doStuff() : void",
                "type": "Expression Statement",
                "expression": "sum+=i;\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 19,
                "endLine": 19,
                "startColumn": 4,
                "endColumn": 13
              }
            },
            {
              "leftSideLocation": {
                "method": "public doStuff() : void",
                "type": "Expression Statement",
                "expression": "sum-=dog.magicNumber;\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 21,
                "endLine": 21,
                "startColumn": 3,
                "endColumn": 26
              },
              "rightSideLocation": {
                "method": "public doStuff() : void",
                "type": "Expression Statement",
                "expression": "sum-=dog.magicNumber;\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 21,
                "endLine": 21,
                "startColumn": 3,
                "endColumn": 26
              }
            },
            {
              "leftSideLocation": {
                "method": "public doStuff() : void",
                "type": "Expression Statement",
                "expression": "dog.takeABreath();\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 22,
                "endLine": 22,
                "startColumn": 3,
                "endColumn": 21
              },
              "rightSideLocation": {
                "method": "public doStuff() : void",
                "type": "Expression Statement",
                "expression": "dog.takeABreath();\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 22,
                "endLine": 22,
                "startColumn": 3,
                "endColumn": 21
              }
            },
            {
              "leftSideLocation": {
                "method": "public doStuff() : void",
                "type": "Variable Declaration Statement",
                "expression": "int age=dog.getAge();\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 15,
                "endLine": 15,
                "startColumn": 3,
                "endColumn": 26
              },
              "rightSideLocation": {
                "method": "public doStuff() : void",
                "type": "Variable Declaration Statement",
                "expression": "int age=dog.getAge();\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 15,
                "endLine": 15,
                "startColumn": 3,
                "endColumn": 26
              }
            },
            {
              "leftSideLocation": {
                "method": "public doStuff() : void",
                "type": "Expression Statement",
                "expression": "this.dog.barkBark(this);\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 13,
                "endLine": 13,
                "startColumn": 3,
                "endColumn": 27
              },
              "rightSideLocation": {
                "method": "public doStuff() : void",
                "type": "Expression Statement",
                "expression": "barkBark(this.dog);\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 13,
                "endLine": 13,
                "startColumn": 3,
                "endColumn": 22
              }
            },
            {
              "leftSideLocation": {
                "method": "public barkBark(manager DogManager) : void",
                "type": "Expression Statement",
                "expression": "System.out.println(\"ruff\");\n",
                "filePath": "src/org/animals/Dog.java",
                "startLine": 15,
                "endLine": 15,
                "startColumn": 3,
                "endColumn": 30
              },
              "rightSideLocation": {
                "method": "public barkBark(dog Dog) : void",
                "type": "Expression Statement",
                "expression": "System.out.println(\"ruff\");\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 26,
                "endLine": 26,
                "startColumn": 3,
                "endColumn": 30
              }
            },
            {
              "leftSideLocation": {
                "method": "public barkBark(manager DogManager) : void",
                "type": "Expression Statement",
                "expression": "System.out.println(\"ruff\");\n",
                "filePath": "src/org/animals/Dog.java",
                "startLine": 18,
                "endLine": 18,
                "startColumn": 3,
                "endColumn": 30
              },
              "rightSideLocation": {
                "method": "public barkBark(dog Dog) : void",
                "type": "Expression Statement",
                "expression": "System.out.println(\"ruff\");\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 29,
                "endLine": 29,
                "startColumn": 3,
                "endColumn": 30
              }
            },
            {
              "leftSideLocation": {
                "method": "public barkBark(manager DogManager) : void",
                "type": "Expression Statement",
                "expression": "System.out.println(\"ruff\");\n",
                "filePath": "src/org/animals/Dog.java",
                "startLine": 16,
                "endLine": 16,
                "startColumn": 3,
                "endColumn": 30
              },
              "rightSideLocation": {
                "method": "public barkBark(dog Dog) : void",
                "type": "Expression Statement",
                "expression": "System.out.println(\"ruff\");\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 27,
                "endLine": 27,
                "startColumn": 3,
                "endColumn": 30
              }
            },
            {
              "leftSideLocation": {
                "method": "public barkBark(manager DogManager) : void",
                "type": "Expression Statement",
                "expression": "System.out.println(\"ruff\");\n",
                "filePath": "src/org/animals/Dog.java",
                "startLine": 20,
                "endLine": 20,
                "startColumn": 3,
                "endColumn": 30
              },
              "rightSideLocation": {
                "method": "public barkBark(dog Dog) : void",
                "type": "Expression Statement",
                "expression": "System.out.println(\"ruff\");\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 31,
                "endLine": 31,
                "startColumn": 3,
                "endColumn": 30
              }
            },
            {
              "leftSideLocation": {
                "method": "public barkBark(manager DogManager) : void",
                "type": "Expression Statement",
                "expression": "System.out.println(\"ruff\");\n",
                "filePath": "src/org/animals/Dog.java",
                "startLine": 19,
                "endLine": 19,
                "startColumn": 3,
                "endColumn": 30
              },
              "rightSideLocation": {
                "method": "public barkBark(dog Dog) : void",
                "type": "Expression Statement",
                "expression": "System.out.println(\"ruff\");\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 30,
                "endLine": 30,
                "startColumn": 3,
                "endColumn": 30
              }
            },
            {
              "leftSideLocation": {
                "method": "public barkBark(manager DogManager) : void",
                "type": "Expression Statement",
                "expression": "takeABreath();\n",
                "filePath": "src/org/animals/Dog.java",
                "startLine": 17,
                "endLine": 17,
                "startColumn": 3,
                "endColumn": 17
              },
              "rightSideLocation": {
                "method": "public barkBark(dog Dog) : void",
                "type": "Expression Statement",
                "expression": "dog.takeABreath();\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 28,
                "endLine": 28,
                "startColumn": 3,
                "endColumn": 21
              }
            }
          ]
        }
      ]
    }
