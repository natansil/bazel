// Copyright 2015 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.packages;

import static com.google.common.truth.Truth.assertThat;

import com.google.devtools.build.lib.cmdline.RepositoryName;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for WorkspaceFactory.
 */
@RunWith(JUnit4.class)
public class WorkspaceFactoryTest {

  @Test
  public void testLoadError() throws Exception {
    // WS with a syntax error: '//a' should end with .bzl.
    WorkspaceFactoryTestHelper helper = parse("load('//a', 'a')");
    helper.assertLexingExceptionThrown();
    assertThat(helper.getLexerError())
        .contains("The label must reference a file with extension '.bzl'");
  }

  @Test
  public void testWorkspaceName() throws Exception {
    WorkspaceFactoryTestHelper helper = parse("workspace(name = 'my_ws')");
    assertThat(helper.getPackage().getWorkspaceName()).isEqualTo("my_ws");
  }

  @Test
  public void testWorkspaceStartsWithNumber() throws Exception {
    WorkspaceFactoryTestHelper helper = parse("workspace(name = '123abc')");
    assertThat(helper.getParserError()).contains("123abc is not a legal workspace name");
  }

  @Test
  public void testWorkspaceWithIllegalCharacters() throws Exception {
    WorkspaceFactoryTestHelper helper = parse("workspace(name = 'a.b.c')");
    assertThat(helper.getParserError()).contains("a.b.c is not a legal workspace name");
  }

  @Test
  public void testIllegalRepoName() throws Exception {
    WorkspaceFactoryTestHelper helper =
        parse("local_repository(", "    name = 'foo/bar',", "    path = '/foo/bar',", ")");
    assertThat(helper.getParserError()).contains(
        "local_repository rule //external:foo/bar's name field must be a legal workspace name");
  }

  @Test
  public void testIllegalWorkspaceFunctionPosition() throws Exception {
    WorkspaceFactoryTestHelper helper =
        new WorkspaceFactoryTestHelper(false, "workspace(name = 'foo')");
    assertThat(helper.getParserError()).contains(
        "workspace() function should be used only at the top of the WORKSPACE file");
  }

  @Test
  public void testRegisterExecutionPlatforms() throws Exception {
    WorkspaceFactoryTestHelper helper = parse("register_execution_platforms('//platform:ep1')");
    assertThat(helper.getPackage().getRegisteredExecutionPlatforms())
        .containsExactly("//platform:ep1");
  }

  @Test
  public void testRegisterExecutionPlatforms_multipleLabels() throws Exception {
    WorkspaceFactoryTestHelper helper =
        parse("register_execution_platforms(", "  '//platform:ep1',", "  '//platform:ep2')");
    assertThat(helper.getPackage().getRegisteredExecutionPlatforms())
        .containsExactly("//platform:ep1", "//platform:ep2");
  }

  @Test
  public void testRegisterExecutionPlatforms_multipleCalls() throws Exception {
    WorkspaceFactoryTestHelper helper =
        parse(
            "register_execution_platforms('//platform:ep1')",
            "register_execution_platforms('//platform:ep2')",
            "register_execution_platforms('//platform/...')");
    assertThat(helper.getPackage().getRegisteredExecutionPlatforms())
        .containsExactly("//platform:ep1", "//platform:ep2", "//platform/...");
  }

  @Test
  public void testRegisterToolchains() throws Exception {
    WorkspaceFactoryTestHelper helper = parse("register_toolchains('//toolchain:tc1')");
    assertThat(helper.getPackage().getRegisteredToolchains()).containsExactly("//toolchain:tc1");
  }

  @Test
  public void testRegisterToolchains_multipleLabels() throws Exception {
    WorkspaceFactoryTestHelper helper =
        parse("register_toolchains(", "  '//toolchain:tc1',", "  '//toolchain:tc2')");
    assertThat(helper.getPackage().getRegisteredToolchains())
        .containsExactly("//toolchain:tc1", "//toolchain:tc2");
  }

  @Test
  public void testRegisterToolchains_multipleCalls() throws Exception {
    WorkspaceFactoryTestHelper helper =
        parse(
            "register_toolchains('//toolchain:tc1')",
            "register_toolchains('//toolchain:tc2')",
            "register_toolchains('//toolchain/...')");
    assertThat(helper.getPackage().getRegisteredToolchains())
        .containsExactly("//toolchain:tc1", "//toolchain:tc2", "//toolchain/...");
  }

  @Test
  public void testWorkspaceMappings() throws Exception {
    WorkspaceFactoryTestHelper helper =
        parse(
            "local_repository(",
            "    name = 'foo',",
            "    path = '/foo',",
            "    repo_mapping = {'@x' : '@y'},",
            ")");
    assertMapping(helper, "@foo", "@x", "@y");
  }

  @Test
  public void testMultipleRepositoriesWithMappings() throws Exception {
    WorkspaceFactoryTestHelper helper =
        parse(
            "local_repository(",
            "    name = 'foo',",
            "    path = '/foo',",
            "    repo_mapping = {'@x' : '@y'},",
            ")",
            "local_repository(",
            "    name = 'bar',",
            "    path = '/bar',",
            "    repo_mapping = {'@a' : '@b'},",
            ")");
    assertMapping(helper, "@foo", "@x", "@y");
    assertMapping(helper, "@bar", "@a", "@b");
  }

  @Test
  public void testMultipleMappings() throws Exception {

    WorkspaceFactoryTestHelper helper =
        parse(
            "local_repository(",
            "    name = 'foo',",
            "    path = '/foo',",
            "    repo_mapping = {'@a' : '@b', '@c' : '@d', '@e' : '@f'},",
            ")");
    assertMapping(helper, "@foo", "@a", "@b");
    assertMapping(helper, "@foo", "@c", "@d");
    assertMapping(helper, "@foo", "@e", "@f");
  }

  @Test
  public void testEmptyMappings() throws Exception {
    WorkspaceFactoryTestHelper helper =
        parse(
            "local_repository(",
            "    name = 'foo',",
            "    path = '/foo',",
            "    repo_mapping = {},",
            ")");
    assertThat(helper.getPackage().getRepositoryMapping("@foo")).isEmpty();
  }

  @Test
  public void testMappingsNotAMap() throws Exception {
    WorkspaceFactoryTestHelper helper =
        parse(
            "local_repository(",
            "    name = 'foo',",
            "    path = '/foo',",
            "    repo_mapping = 1",
            ")");
    assertThat(helper.getParserError())
        .contains("Invalid value for 'repo_mapping': '1'. Value must be a map.");

    helper =
        parse(
            "local_repository(",
            "    name = 'foo',",
            "    path = '/foo',",
            "    repo_mapping = 'hello'",
            ")");
    assertThat(helper.getParserError())
        .contains("Invalid value for 'repo_mapping': 'hello'. Value must be a map.");
  }

  private void assertMapping(
      WorkspaceFactoryTestHelper helper, String repo, String local, String global)
      throws Exception {
    RepositoryName localRepoName = RepositoryName.create(local);
    RepositoryName globalRepoName = RepositoryName.create(global);
    assertThat(helper.getPackage().getRepositoryMapping(repo))
        .containsEntry(localRepoName, globalRepoName);
  }

  private WorkspaceFactoryTestHelper parse(String... args) {
    return new WorkspaceFactoryTestHelper(args);
  }

}
