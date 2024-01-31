Table of Contents
=================

   * [General info](#general-info)
   * [Requirements](#requirements)
   * [API usage guidelines](#api-usage-guidelines)
     * [With a locally cloned git repository](#with-a-locally-cloned-git-repository)
     * [With two files containing Java source code](#with-two-files-containing-java-source-code)

   * [Location information](#location-information)
   * [How to build and run](#how-to-build-and-run)
     * [Command line](#command-line)
     * [IntelliJ IDEA](#intellij-idea)
   * [How to add as a Maven dependency](#how-to-add-as-a-maven-dependency)
   * [How to cite ReMapper](#how-to-cite-remapper)
   * [Data](#data)


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

10. Statements

# Requirements

**Java 17** or newer

**Apache Maven 3.8** or newer

# API usage guidelines

## With a locally cloned git repository

ReMapper can automatically match code entities between two successive versions of a git repository.

In the code snippet below we demonstrate how to print all matched code entities at a specific commit in the toy project https://github.com/danilofes/refactoring-toy-example.git. The commit is identified by its SHA key, such as in the example below:

```java
GitService gitService = new GitServiceImpl();
try (Repository repo = gitService.openRepository("E:/refactoring-toy-example")) {
  EntityMatcherService matcher = new EntityMatcherServiceImpl();
  matcher.matchAtCommit(repo, "d4bce13a443cf12da40a77c16c1e591f4f985b47", new MatchingHandler() {
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

You can use the following code snippet to print all removed, newly added, and intact (unchanged) entities at a specific commit.

```java
matcher.matchAtCommit(repo, "d4bce13a443cf12da40a77c16c1e591f4f985b47", new MatchingHandler() {
  @Override
  public void handle(String commitId, MatchPair matchPair) {
    
    / ** print all removed entities */
    for (EntityInfo entity : matchPair.getDeletedEntityInfos()) {
      System.out.println("Removed entity --> " + entity);
      System.out.println();
    }
    
    / ** print all added entities */
    for (EntityInfo entity : matchPair.getAddedEntityInfos()) {
      System.out.println("Added entity --> " + entity);
      System.out.println();
    }
    
    / ** print all intact entities */  
    for (Pair<EntityInfo, EntityInfo> pair : matchPair.getUnchangedEntityInfos()) {
      System.out.println("Old entity --> " + pair.getLeft());
      System.out.println("New entity --> " + pair.getRight());
      System.out.println();
    }
  }
});
```

You can use the following code snippet to print all matched, removed, and newly added statements at a specific commit.

```java
matcher.matchAtCommit(repo, "d4bce13a443cf12da40a77c16c1e591f4f985b47", new MatchingHandler() {
  @Override
  public void handle(String commitId, MatchPair matchPair) {
    
    / ** print all matched statements */
    for (Pair<StatementInfo, StatementInfo> pair : matchPair.getMatchedStatementInfos()) {
      System.out.println("Old statement --> " + pair.getLeft());
      System.out.println("New statement --> " + pair.getRight());
      System.out.println();
    }
      
    / ** print all removed statements */
    for (StatementInfo statement : matchPair.getDeletedStatementInfos()) {
      System.out.println("Removed statement --> " + statement);
      System.out.println();
    }
    
    / ** print all added statements */
    for (StatementInfo statement : matchPair.getAddedStatementInfos()) {
      System.out.println("Added statement --> " + statement);
      System.out.println();
    }
  }
});
```

## With two files containing Java source code

It is possible to match code entities between two Java files containing the code before and after some changes:

```java
EntityMatcherService matcher = new EntityMatcherServiceImpl();
// You must provide absolute paths to the files.
File file1 = new File("/home/user/tmp/v1");
File file2 = new File("/home/user/tmp/v2");
matcher.matchAtFiles(file1, file2, new MatchingHandler() {
  @Override
  public void handle(String commitId, MatchPair matchPair) {
    System.out.println("Matched code entities at " + commitId);
    for (Pair<EntityInfo, EntityInfo> pair : matchPair.getMatchedEntityInfos()) {
      System.out.println("Old entity --> " + pair.getLeft());
      System.out.println("New entity --> " + pair.getRight());
      System.out.println();
    }
  }
});
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
                "endColumn": 3,
                "codeElementType": "METHOD_DECLARATION"
              },
              "rightSideLocation": {
                "container": "org.DogManager",
                "type": "Method",
                "name": "doStuff",
                "filePath": "src/org/DogManager.java",
                "startLine": 12,
                "endLine": 23,
                "startColumn": 2,
                "endColumn": 3,
                "codeElementType": "METHOD_DECLARATION"
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
                "endColumn": 2,
                "codeElementType": "TYPE_DECLARATION"
              },
              "rightSideLocation": {
                "container": "org.DogManager",
                "type": "Class",
                "name": "DogManager",
                "filePath": "src/org/DogManager.java",
                "startLine": 5,
                "endLine": 34,
                "startColumn": 0,
                "endColumn": 2,
                "codeElementType": "TYPE_DECLARATION"
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
                "endColumn": 2,
                "codeElementType": "TYPE_DECLARATION"
              },
              "rightSideLocation": {
                "container": "org.animals.Dog",
                "type": "Class",
                "name": "Dog",
                "filePath": "src/org/animals/Dog.java",
                "startLine": 4,
                "endLine": 17,
                "startColumn": 0,
                "endColumn": 2,
                "codeElementType": "TYPE_DECLARATION"
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
                "endColumn": 3,
                "codeElementType": "METHOD_DECLARATION"
              },
              "rightSideLocation": {
                "container": "org.DogManager",
                "type": "Method",
                "name": "barkBark",
                "filePath": "src/org/DogManager.java",
                "startLine": 25,
                "endLine": 32,
                "startColumn": 2,
                "endColumn": 3,
                "codeElementType": "METHOD_DECLARATION"
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
                "endColumn": 4,
                "codeElementType": "FOR_STATEMENT"
              },
              "rightSideLocation": {
                "method": "public doStuff() : void",
                "type": "For Statement",
                "expression": "for(int i=0; i < age; i++)",
                "filePath": "src/org/DogManager.java",
                "startLine": 17,
                "endLine": 20,
                "startColumn": 3,
                "endColumn": 4,
                "codeElementType": "FOR_STATEMENT"
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
                "endColumn": 4,
                "codeElementType": "BLOCK"
              },
              "rightSideLocation": {
                "method": "public doStuff() : void",
                "type": "Block",
                "expression": "{",
                "filePath": "src/org/DogManager.java",
                "startLine": 17,
                "endLine": 20,
                "startColumn": 33,
                "endColumn": 4,
                "codeElementType": "BLOCK"
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
                "endColumn": 26,
                "codeElementType": "EXPRESSION_STATEMENT"
              },
              "rightSideLocation": {
                "method": "public doStuff() : void",
                "type": "Expression Statement",
                "expression": "System.out.println(i);\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 18,
                "endLine": 18,
                "startColumn": 4,
                "endColumn": 26,
                "codeElementType": "EXPRESSION_STATEMENT"
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
                "endColumn": 13,
                "codeElementType": "EXPRESSION_STATEMENT"
              },
              "rightSideLocation": {
                "method": "public doStuff() : void",
                "type": "Expression Statement",
                "expression": "sum+=i;\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 19,
                "endLine": 19,
                "startColumn": 4,
                "endColumn": 13,
                "codeElementType": "EXPRESSION_STATEMENT"
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
                "endColumn": 26,
                "codeElementType": "VARIABLE_DECLARATION_STATEMENT"
              },
              "rightSideLocation": {
                "method": "public doStuff() : void",
                "type": "Variable Declaration Statement",
                "expression": "int age=dog.getAge();\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 15,
                "endLine": 15,
                "startColumn": 3,
                "endColumn": 26,
                "codeElementType": "VARIABLE_DECLARATION_STATEMENT"
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
                "endColumn": 15,
                "codeElementType": "VARIABLE_DECLARATION_STATEMENT"
              },
              "rightSideLocation": {
                "method": "public doStuff() : void",
                "type": "Variable Declaration Statement",
                "expression": "int sum=0;\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 16,
                "endLine": 16,
                "startColumn": 3,
                "endColumn": 15,
                "codeElementType": "VARIABLE_DECLARATION_STATEMENT"
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
                "endColumn": 26,
                "codeElementType": "EXPRESSION_STATEMENT"
              },
              "rightSideLocation": {
                "method": "public doStuff() : void",
                "type": "Expression Statement",
                "expression": "sum-=dog.magicNumber;\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 21,
                "endLine": 21,
                "startColumn": 3,
                "endColumn": 26,
                "codeElementType": "EXPRESSION_STATEMENT"
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
                "endColumn": 21,
                "codeElementType": "EXPRESSION_STATEMENT"
              },
              "rightSideLocation": {
                "method": "public doStuff() : void",
                "type": "Expression Statement",
                "expression": "dog.takeABreath();\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 22,
                "endLine": 22,
                "startColumn": 3,
                "endColumn": 21,
                "codeElementType": "EXPRESSION_STATEMENT"
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
                "endColumn": 27,
                "codeElementType": "EXPRESSION_STATEMENT"
              },
              "rightSideLocation": {
                "method": "public doStuff() : void",
                "type": "Expression Statement",
                "expression": "barkBark(this.dog);\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 13,
                "endLine": 13,
                "startColumn": 3,
                "endColumn": 22,
                "codeElementType": "EXPRESSION_STATEMENT"
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
                "endColumn": 30,
                "codeElementType": "EXPRESSION_STATEMENT"
              },
              "rightSideLocation": {
                "method": "public barkBark(dog Dog) : void",
                "type": "Expression Statement",
                "expression": "System.out.println(\"ruff\");\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 26,
                "endLine": 26,
                "startColumn": 3,
                "endColumn": 30,
                "codeElementType": "EXPRESSION_STATEMENT"
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
                "endColumn": 30,
                "codeElementType": "EXPRESSION_STATEMENT"
              },
              "rightSideLocation": {
                "method": "public barkBark(dog Dog) : void",
                "type": "Expression Statement",
                "expression": "System.out.println(\"ruff\");\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 27,
                "endLine": 27,
                "startColumn": 3,
                "endColumn": 30,
                "codeElementType": "EXPRESSION_STATEMENT"
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
                "endColumn": 30,
                "codeElementType": "EXPRESSION_STATEMENT"
              },
              "rightSideLocation": {
                "method": "public barkBark(dog Dog) : void",
                "type": "Expression Statement",
                "expression": "System.out.println(\"ruff\");\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 29,
                "endLine": 29,
                "startColumn": 3,
                "endColumn": 30,
                "codeElementType": "EXPRESSION_STATEMENT"
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
                "endColumn": 30,
                "codeElementType": "EXPRESSION_STATEMENT"
              },
              "rightSideLocation": {
                "method": "public barkBark(dog Dog) : void",
                "type": "Expression Statement",
                "expression": "System.out.println(\"ruff\");\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 30,
                "endLine": 30,
                "startColumn": 3,
                "endColumn": 30,
                "codeElementType": "EXPRESSION_STATEMENT"
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
                "endColumn": 30,
                "codeElementType": "EXPRESSION_STATEMENT"
              },
              "rightSideLocation": {
                "method": "public barkBark(dog Dog) : void",
                "type": "Expression Statement",
                "expression": "System.out.println(\"ruff\");\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 31,
                "endLine": 31,
                "startColumn": 3,
                "endColumn": 30,
                "codeElementType": "EXPRESSION_STATEMENT"
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
                "endColumn": 17,
                "codeElementType": "EXPRESSION_STATEMENT"
              },
              "rightSideLocation": {
                "method": "public barkBark(dog Dog) : void",
                "type": "Expression Statement",
                "expression": "dog.takeABreath();\n",
                "filePath": "src/org/DogManager.java",
                "startLine": 28,
                "endLine": 28,
                "startColumn": 3,
                "endColumn": 21,
                "codeElementType": "EXPRESSION_STATEMENT"
              }
            }
          ]
        }
      ]
    }

# How to add as a maven dependency

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.lyoubo/remapper/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.lyoubo/remapper)

To add ReMapper as a maven dependency in your project, add the following snippet to your project's pom.xml:

    <dependency>
      <groupId>io.github.lyoubo</groupId>
      <artifactId>remapper</artifactId>
      <version>2.1.15</version>
    </dependency>

# How to cite ReMapper

If you are using ReMapper in your research, please cite the following papers:

Bo Liu, Hui Liu, Nan Niu, Yuxia Zhang, Guangjie Li, and Yanjie Jiang, "[Automated Software Entity Matching Between Successive Versions](#https://lyoubo.github.io/papers/Automated_Software_Entity_Matching_Between_Successive_Versions.pdf),"
*38th IEEE/ACM International Conference on Automated Software Engineering (ASE 2023)*, September 11-15, 2023, Kirchberg, Luxembourg.

    @inproceedings{liu2023automated,
      title={Automated Software Entity Matching Between Successive Versions},
      author={Liu, Bo and Liu, Hui and Niu, Nan and Zhang, Yuxia and Li, Guangjie and Jiang, Yanjie},
      booktitle={Proceedings of the 38th IEEE/ACM International Conference on Automated Software Engineering},
      series={ASE '23},
      pages={1615--1627},
      year={2023},
      publisher={IEEE},
      doi={10.1109/ASE56229.2023.00132}
    }

# Data

### 1. Entity Matching

All results reported by the proposed approach and the baseline approach as well as the labels manually validated by the experienced developers, are available at the following links:

* [entity matching](data/entity%20matching/)

Each JSON file represents the results of running entity matching experiments of the proposed approach and the baseline approach separately in a project.

#### &emsp;JSON property descriptions

&emsp;<font size=2>**repository**: Git repository name</font>  
&emsp;<font size=2>**sha1**: Git commit ID</font>  
&emsp;<font size=2>**url**: patch corresponding to the commit</font>  
&emsp;<font size=2>**commonMatching**: common matched entity pairs reported by the evaluated approaches</font>  
&emsp;<font size=2>**ourApproach**: inconsistent entity pairs reported by the proposed approach against the baseline approach</font>  
&emsp;<font size=2>**baseline**: inconsistent entity pairs reported by the baseline approach against the proposed approach</font>  
&emsp;<font size=2>**leftSideLocation**: position of the entity in the old version</font>  
&emsp;<font size=2>**rightSideLocation**: position of the entity in the new version</font>  
&emsp;<font size=2>**container**: container in which entity belongs to</font>  
&emsp;<font size=2>**type**: type of entity</font>  
&emsp;<font size=2>**name**: name of entity</font>  
&emsp;<font size=2>**filePath**: file path in which the entity is declared</font>  
&emsp;<font size=2>**startLine**: start line of entity declaration</font>  
&emsp;<font size=2>**endLine**: end line of entity declaration</font>  
&emsp;<font size=2>**startColumn**: start column of entity declaration</font>  
&emsp;<font size=2>**endColumn**: end column of entity declaration</font>  
&emsp;<font size=2>**developerConfirmation**: label manually validated by the developers</font>

### 2. Refactoring Discovery

All results reported by the proposed approach and the baseline approach as well as the labels manually validated by the refactoring experts, are available at the following links:

* [refactoring discovery](data/refactoring%20discovery/)

Each JSON file represents the results of running refactoring discovery experiments of the proposed approach and the baseline approach separately in a project.

#### &emsp;JSON property descriptions

&emsp;<font size=2>**repository**: Git repository name</font>  
&emsp;<font size=2>**sha1**: Git commit ID</font>    
&emsp;<font size=2>**url**: patch corresponding to the commit</font>    
&emsp;<font size=2>**ourApproach**: refactoring operations reported by the proposed approach</font>    
&emsp;<font size=2>**baseline**: refactoring operations reported by the baseline approach</font>    
&emsp;<font size=2>**type**: type of refactoring</font>    
&emsp;<font size=2>**description**: description of refactoring</font>    
&emsp;<font size=2>**leftSideLocation**: position of the entity in the old version</font>    
&emsp;<font size=2>**rightSideLocation**: position of the entity in the new version</font>    
&emsp;<font size=2>**filePath**: file path in which the entity is declared</font>    
&emsp;<font size=2>**startLine**: start line of entity declaration</font>    
&emsp;<font size=2>**endLine**: end line of entity declaration</font>    
&emsp;<font size=2>**startColumn**: start column of entity declaration</font>    
&emsp;<font size=2>**endColumn**: end column of entity declaration</font>    
&emsp;<font size=2>**developerConfirmation**: label manually validated by the experts</font>
