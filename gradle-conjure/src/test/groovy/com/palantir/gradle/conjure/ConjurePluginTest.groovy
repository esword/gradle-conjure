/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.gradle.conjure


import nebula.test.IntegrationSpec
import nebula.test.functional.ExecutionResult
import spock.lang.IgnoreIf
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

class ConjurePluginTest extends IntegrationSpec {

    def setup() {
        createFile('settings.gradle') << '''
        include 'api'
        include 'api:api-objects'
        include 'api:api-jersey'
        include 'api:api-retrofit'
        include 'api:api-typescript'
        include 'api:api-undertow'
        include 'api:api-dialogue'
        include 'server'
        '''.stripIndent()

        buildFile << """
        buildscript {
            repositories {
                mavenCentral()
                maven { url 'https://dl.bintray.com/palantir/releases/' }
                gradlePluginPortal()
            }
            dependencies {
                classpath 'com.palantir.baseline:gradle-baseline-java:3.23.0'
            }
        }
        
        allprojects {
            version '0.1.0'
            group 'com.palantir.conjure.test'

            repositories {
                mavenCentral()
                maven { url 'https://dl.bintray.com/palantir/releases/' }
            }

            configurations.all {
               resolutionStrategy {
                   force 'com.palantir.conjure.java:conjure-java:${TestVersions.CONJURE_JAVA}'
                   force 'com.palantir.conjure.java:conjure-lib:${TestVersions.CONJURE_JAVA}'
                   force 'com.palantir.conjure.java:conjure-undertow-lib:${TestVersions.CONJURE_JAVA}'
                   force 'com.palantir.conjure:conjure:${TestVersions.CONJURE}'
                   force 'com.palantir.conjure.typescript:conjure-typescript:${TestVersions.CONJURE_TYPESCRIPT}'

                   force 'com.fasterxml.jackson.core:jackson-annotations:2.10.2'
                   force 'com.fasterxml.jackson.core:jackson-databind:2.10.2'
                   force 'com.google.guava:guava:23.6.1-jre'
                   force 'com.palantir.safe-logging:preconditions:1.12.0'
                   force 'com.palantir.safe-logging:safe-logging:1.12.0'
                   force 'com.squareup.retrofit2:retrofit:2.1.0'
               }
           }
        }
        """.stripIndent()

        createFile('api/build.gradle') << '''
        apply plugin: 'com.palantir.conjure'
        '''.stripIndent()

        createFile('api/src/main/conjure/api.yml') << '''
        types:
          definitions:
            default-package: test.test.api
            objects:
              StringExample:
                fields:
                  string: string
        services:
          TestServiceFoo:
            name: Test Service Foo
            package: test.test.api

            endpoints:
              post:
                http: POST /post
                args:
                  object: StringExample
                returns: StringExample
        '''.stripIndent()
        file("gradle.properties") << "org.gradle.daemon=false"
    }

    def 'compileConjure generates code and ir in subprojects'() {
        when:
        ExecutionResult result = runTasksSuccessfully(':api:compileConjure')

        then:
        result.wasExecuted(':api:compileConjure')
        result.wasExecuted(':api:compileConjureObjects')
        result.wasExecuted(':api:compileConjureJersey')
        result.wasExecuted(':api:compileConjureRetrofit')
        result.wasExecuted(':api:compileConjureTypeScript')
        result.wasExecuted(':api:compileConjureUndertow')
        result.wasExecuted(':api:compileConjureDialogue')
        result.wasExecuted(':api:compileIr')

        // java
        fileExists('api/api-objects/src/generated/java/test/test/api/StringExample.java')
        file('api/api-objects/src/generated/java/test/test/api/StringExample.java').text.contains('ignoreUnknown')
        fileExists('api/api-objects/.gitignore')
        file('api/api-objects/.gitignore').readLines() == ['/src/generated/java/']

        // typescript
        fileExists('api/api-typescript/src/api/index.ts')
        fileExists('api/api-typescript/src/index.ts')
        fileExists('api/api-typescript/src/tsconfig.json')
        fileExists('api/api-typescript/src/package.json')
        fileExists('api/api-typescript/.gitignore')
        file('api/api-typescript/.gitignore').readLines() == ["/src/"]

        // irFile
        fileExists('api/build/conjure-ir/api.conjure.json')
        file('api/build/conjure-ir/api.conjure.json').text.contains('TestServiceFoo')
    }

    def 'check code compiles'() {
        when:
        ExecutionResult result = runTasksSuccessfully(':api:api-dialogue:dependencies', 'check', '-s')

        then:
        result.wasExecuted(':api:api-objects:compileJava')
        result.wasExecuted(':api:compileConjureObjects')
        result.wasExecuted(':api:api-jersey:compileJava')
        result.wasExecuted(':api:compileConjureJersey')
        result.wasExecuted(':api:api-retrofit:compileJava')
        result.wasExecuted(':api:compileConjureRetrofit')
        result.wasExecuted(':api:api-undertow:compileJava')
        result.wasExecuted(':api:compileConjureUndertow')
        result.wasExecuted(':api:api-dialogue:compileJava')
        result.wasExecuted(':api:compileConjureDialogue')

        fileExists('api/api-objects/src/generated/java/test/test/api/StringExample.java')
        fileExists('api/api-objects/.gitignore')
    }

    def 'check cache is used'() {
        when:
        ExecutionResult result = runTasksSuccessfully('check')
        ExecutionResult result2 = runTasksSuccessfully('check')

        then:
        result.wasExecuted(':extractConjureJava')
        result.wasExecuted(':api:api-objects:compileJava')
        result.wasExecuted(':api:compileConjureObjects')
        result.wasExecuted(':api:api-jersey:compileJava')
        result.wasExecuted(':api:compileConjureJersey')
        result.wasExecuted(':api:api-retrofit:compileJava')
        result.wasExecuted(':api:compileConjureRetrofit')
        result.wasExecuted(':api:api-undertow:compileJava')
        result.wasExecuted(':api:compileConjureUndertow')
        result.wasExecuted(':api:api-dialogue:compileJava')
        result.wasExecuted(':api:compileConjureDialogue')

        result2.wasUpToDate(':extractConjureJava')
        result2.wasUpToDate(':api:api-objects:compileJava')
        result2.wasUpToDate(':api:compileConjureObjects')
        result2.wasUpToDate(':api:api-jersey:compileJava')
        result2.wasUpToDate(':api:compileConjureJersey')
        result2.wasUpToDate(':api:api-retrofit:compileJava')
        result2.wasUpToDate(':api:compileConjureRetrofit')
        result2.wasUpToDate(':api:api-undertow:compileJava')
        result2.wasUpToDate(':api:compileConjureUndertow')
        result2.wasUpToDate(':api:api-dialogue:compileJava')
        result2.wasUpToDate(':api:compileConjureDialogue')
    }

    def 'check code compiles when run in parallel with multiple build targets'() {
        when:
        System.setProperty("ignoreMutableProjectStateWarnings", "true")
        ExecutionResult result = runTasksSuccessfully('--parallel', 'check', 'tasks')

        then:
        result.wasExecuted(':api:api-objects:compileJava')
        result.wasExecuted(':api:api-jersey:compileJava')
        result.wasExecuted(':api:compileConjureJersey')

        fileExists('api/api-objects/src/generated/java/test/test/api/StringExample.java')
        fileExists('api/api-objects/.gitignore')
    }

    def 'clean cleans up src/generated/java'() {
        when:
        runTasksSuccessfully('compileJava')
        ExecutionResult result = runTasksSuccessfully('clean')

        then:
        result.wasExecuted(':api:cleanCompileConjureJersey')
        result.wasExecuted(':api:cleanCompileConjureObjects')
        result.wasExecuted(':api:cleanCompileConjureRetrofit')
        result.wasExecuted(':api:cleanCompileConjureUndertow')
        result.wasExecuted(':api:cleanCompileConjureDialogue')

        !fileExists('api/api-jersey/src/generated/java')
        !fileExists('api/api-objects/src/generated/java')
        !fileExists('api/api-retrofit/src/generated/java')
        !fileExists('api/api-undertow/src/generated/java')
        !fileExists('api/api-dialogue/src/generated/java')
    }

    def 'compileConjure creates build/conjure for root project'() {
        when:
        runTasksSuccessfully('compileConjure')

        then:
        fileExists('api/build/conjure')
    }

    def 'clean cleans up build/conjure for root project'() {
        when:
        runTasksSuccessfully('compileConjure')
        ExecutionResult result = runTasksSuccessfully('clean')

        then:
        result.wasExecuted(':api:cleanCopyConjureSourcesIntoBuild')

        !fileExists('api/build/conjure')
    }

    def 'compileConjure does not run tasks if up to date'() {
        when:
        runTasksSuccessfully("compileConjure")
        ExecutionResult result = runTasksSuccessfully("compileConjure")

        then:
        result.wasUpToDate(':api:api-objects:gitignoreConjureObjects')
        result.wasUpToDate(':api:api-jersey:gitignoreConjureJersey')
        result.wasUpToDate(':api:api-retrofit:gitignoreConjureRetrofit')
        result.wasUpToDate(':api:api-typescript:gitignoreConjureTypeScript')
        result.wasUpToDate(':api:api-undertow:gitignoreConjureUndertow')
        result.wasUpToDate(':api:api-dialogue:gitignoreConjureDialogue')
        result.wasUpToDate(':api:compileConjureObjects')
        result.wasUpToDate(':api:compileConjureJersey')
        result.wasUpToDate(':api:compileConjureRetrofit')
        result.wasUpToDate(':api:compileConjureTypeScript')
        result.wasUpToDate(':api:compileConjureUndertow')
        result.wasUpToDate(':api:compileConjureDialogue')
        result.wasUpToDate(':api:copyConjureSourcesIntoBuild')
        result.wasUpToDate(':api:compileIr')
    }

    def 'compileConjure does run tasks if not up to date'() {
        when:
        runTasksSuccessfully("compileConjure")
        createFile('api/src/main/conjure/api.yml').write '''
        types:
          definitions:
            default-package: test.test.api
            objects:
              StringExample:
                fields:
                  string: string
        services:
          TestServiceFoo:
            name: Changed name of Test Service Foo
            package: test.test.api

            endpoints:
              post:
                http: POST /post
                args:
                  object: StringExample
                returns: StringExample
        '''.stripIndent()
        ExecutionResult result = runTasksSuccessfully("compileConjure")

        then:
        result.wasExecuted(':api:compileConjureObjects')
        result.wasExecuted(':api:compileConjureJersey')
        result.wasExecuted(':api:compileConjureRetrofit')
        result.wasExecuted(':api:compileConjureTypeScript')
        result.wasExecuted(':api:compileConjureUndertow')
        result.wasExecuted(':api:compileConjureDialogue')
        result.wasExecuted(':api:copyConjureSourcesIntoBuild')
    }

    def 'conjure files which no longer exist are removed from build dir'() {
        when:
        String path = 'api/src/main/conjure/todelete.yml'
        createFile(path) << '''
        types:
          definitions:
            default-package: test.a.api
            objects:
              UnionTypeExample:
                union:
                  number: integer
        '''.stripIndent()
        runTasksSuccessfully("copyConjureSourcesIntoBuild")
        file(path).delete()
        runTasksSuccessfully("copyConjureSourcesIntoBuild")

        then:
        !fileExists('api/build/conjure/todelete.yml')
    }

    def 'copies conjure imports into build directory and provides imports to conjure compiler'() {
        createFile('api/src/main/conjure/conjure.yml') << '''
        types:
          conjure-imports:
            internalImport: internal-import.yml
          definitions:
            default-package: test.api.default
            objects:

        services:
          TestServiceFoo2:
            name: Test Service Foo
            package: test.api.service

            endpoints:
              post:
                http: POST /post
                args:
                  object: internalImport.InternalImport
                returns: internalImport.InternalImport
        '''.stripIndent()

        createFile('api/src/main/conjure/internal-import.yml') << '''
        types:
          definitions:
            default-package: test.api.internal
            objects:
              InternalImport:
                fields:
                  stringField: string
        '''.stripIndent()

        when:
        ExecutionResult result = runTasksSuccessfully(':api:compileConjure')

        then:
        result.wasExecuted(':api:compileConjure')
        result.wasExecuted(':api:compileConjureJersey')
        result.wasExecuted(':api:compileConjureObjects')
        result.wasExecuted(':api:compileConjureRetrofit')
        result.wasExecuted(":api:compileIr")

        fileExists('api/build/conjure/internal-import.yml')
        fileExists('api/build/conjure/conjure.yml')

        // java
        file('api/api-jersey/src/generated/java/test/api/service/TestServiceFoo2.java').text.contains(
                'import test.api.internal.InternalImport;')
        file('api/api-retrofit/src/generated/java/test/api/service/TestServiceFoo2Retrofit.java').text.contains(
                'import test.api.internal.InternalImport;')
        fileExists('api/api-objects/src/generated/java/test/api/internal/InternalImport.java')

        // typescript
        file('api/api-typescript/src/service/testServiceFoo2.ts').text.contains(
                'import { IInternalImport }')

        // ir
        fileExists("api/build/conjure-ir/api.conjure.json")
    }

    def 'omitting a project from settings is sufficient to disable'() {
        given:
        file('settings.gradle').text = '''
        include 'api'
        include 'api:api-objects'
        '''.stripIndent()

        when:
        ExecutionResult result = runTasksSuccessfully(':api:compileConjure')

        then:
        result.wasExecuted(':api:compileConjure')
        result.wasExecuted(':api:compileConjureObjects')
        !result.wasExecuted(':api:compileConjureJersey')

        fileExists('api/api-objects/src/generated/java/test/test/api/StringExample.java')
        file('api/api-objects/src/generated/java/test/test/api/StringExample.java').text.contains('ignoreUnknown')
    }

    def 'including only the jersey project throws because objects project is missing'() {
        given:
        file('settings.gradle').text = '''
        include 'api'
        include 'api:api-jersey'
        '''.stripIndent()

        when:
        ExecutionResult result = runTasksWithFailure(':api:compileConjure')

        then:
        !result.wasExecuted(':api:compileConjureJersey')
    }


    def 'featureFlag UndertowServicePrefix can be enabled'() {
        file('api/build.gradle') << '''
        conjure {
            java {
                undertowServicePrefixes = true
            }
        }
        '''.stripIndent()

        when:
        ExecutionResult result = runTasksSuccessfully(':api:compileConjureUndertow')

        then:
        fileExists('api/api-undertow/src/generated/java/test/test/api/UndertowTestServiceFoo.java')
    }

    def 'typescript extension is respected'() {
         file('api/build.gradle') << '''
        conjure {
            typescript {
                packageName = "foo"
                version = "0.0.0"
                nodeCompatibleModules = true
            }
        }
        '''.stripIndent()

        when:
        ExecutionResult result = runTasksSuccessfully(':api:compileConjureTypeScript')

        then:
        file('api/api-typescript/src/package.json').text.contains('"name": "foo"')
        file('api/api-typescript/src/package.json').text.contains('"version": "0.0.0"')
        file('api/api-typescript/src/tsconfig.json').text.contains('"module": "commonjs"')
    }

    def 'passes additional option when running compile task'() {
        file('api/build.gradle') << '''
        conjure {
            typescript {
                nodeCompatibleModules = true
                unknownOps = "Unknown"
            }
        }
        '''.stripIndent()

        when:
        ExecutionResult result = runTasks(':api:compileConjureTypeScript')

        then:
        result.standardOutput.contains("--nodeCompatibleModules")
        result.standardOutput.contains("--unknownOps=Unknown")
    }

    def 'works with afterEvaluate'() {
        file('build.gradle') << '''
            allprojects {
                afterEvaluate { p ->
                    if (p.tasks.findByPath('check') == null) {
                        p.tasks.create('check')
                    }
                }
            }
        '''.stripIndent()

        when:
        // doesn't matter what task is run, just need to trigger project evaluation
        ExecutionResult result = runTasksSuccessfully(':tasks')

        then:
        result.success
    }

    def 'supports generic generators'() {
        addSubproject(':api:api-postman')
        file('api/build.gradle') << """
        dependencies {
            conjureGenerators 'com.palantir.conjure.postman:conjure-postman:${TestVersions.CONJURE_POSTMAN}'
        }

        conjure {
            options "postman", {
                productName = project.name
                productVersion = '1.0.0'
            }
        }
        """.stripIndent()

        when:
        ExecutionResult result = runTasksSuccessfully(':api:compileConjure')

        then:
        result.wasExecuted(':api:compileConjurePostman')
        fileExists('api/api-postman/src/api.postman_collection.json')
        file('api/api-postman/src/api.postman_collection.json').text.contains('"version" : "1.0.0"')
    }

    def 'generic setup is a no-op if there no generic subprojects'() {
        given:
        file('api/build.gradle') << """
        dependencies {
            // The following will cause configuration to fail
            conjureGenerators 'com.google.guava:guava'
        }
        """.stripIndent()

        expect:
        runTasksSuccessfully('compileConjure')
    }

    def 'sets up idea source sets correctly'() {
        given:
        createFile('api/api-jersey/some-extra-source-folder')

        file('build.gradle') << '''
        subprojects {
            apply plugin: 'idea'

            idea {
                module {
                    sourceDirs += file('some-extra-source-folder')
                }
            }
        }
        '''.stripIndent()

        when:
        runTasksSuccessfully('idea')

        then:
        def slurper = new XmlParser()
        def module = slurper.parse(file('api/api-jersey/api-jersey.iml'))
        def sourcesFolderUrls = module.component.content.sourceFolder.@url

        sourcesFolderUrls.size() == 2
        sourcesFolderUrls.contains('file://$MODULE_DIR$/some-extra-source-folder')
        sourcesFolderUrls.contains('file://$MODULE_DIR$/src/generated/java')
    }

    @RestoreSystemProperties
    def 'works with checkUnusedDependencies'() {
        System.setProperty("ignoreMutableProjectStateWarnings", "true")
        buildFile << """
            allprojects { apply plugin: 'com.palantir.baseline-exact-dependencies' }
        """.stripIndent()

        expect:
        runTasksSuccessfully('checkUnusedDependencies', '--warning-mode=all')
    }

    @Unroll
    @IgnoreIf({ jvm.java11Compatible })
    def 'runs on version of gradle: #version'() {
        when:
        gradleVersion = version
        ExecutionResult result = runTasksSuccessfully('compileConjure')

        then:
        result.success

        where:
        version << ['6.1']
    }
}
