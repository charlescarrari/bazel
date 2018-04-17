// Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.lib.rules.android;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.actions.Artifact;
import com.google.devtools.build.lib.actions.Artifact.SpecialArtifact;
import com.google.devtools.build.lib.analysis.FileProvider;
import com.google.devtools.build.lib.analysis.RuleContext;
import com.google.devtools.build.lib.analysis.TransitiveInfoCollection;
import com.google.devtools.build.lib.analysis.configuredtargets.RuleConfiguredTarget.Mode;
import com.google.devtools.build.lib.packages.RuleClass.ConfiguredTargetFactory.RuleErrorException;
import com.google.devtools.build.lib.syntax.Type;
import com.google.devtools.build.lib.vfs.PathFragment;
import java.util.Objects;

/** Wraps this target's Android assets */
public class AndroidAssets {
  private static final String ASSETS_ATTR = "assets";
  private static final String ASSETS_DIR_ATTR = "assets_dir";

  static void validateAssetsAndAssetsDir(RuleContext ruleContext) throws RuleErrorException {
    if (ruleContext.attributes().isAttributeValueExplicitlySpecified(ASSETS_ATTR)
        ^ ruleContext.attributes().isAttributeValueExplicitlySpecified(ASSETS_DIR_ATTR)) {
      ruleContext.throwWithRuleError(
          String.format(
              "'%s' and '%s' should be either both empty or both non-empty",
              ASSETS_ATTR, ASSETS_DIR_ATTR));
    }
  }

  /** Collects this rule's android assets. */
  public static AndroidAssets from(RuleContext ruleContext) throws RuleErrorException {
    validateAssetsAndAssetsDir(ruleContext);

    if (!ruleContext.attributes().has(ASSETS_ATTR)) {
      return new AndroidAssets(ImmutableList.of(), ImmutableList.of());
    }

    PathFragment assetsDir = getAssetDir(ruleContext);

    ImmutableList.Builder<Artifact> assets = ImmutableList.builder();
    ImmutableList.Builder<PathFragment> assetRoots = ImmutableList.builder();

    for (TransitiveInfoCollection target :
        ruleContext.getPrerequisitesIf(ASSETS_ATTR, Mode.TARGET, FileProvider.class)) {
      for (Artifact file : target.getProvider(FileProvider.class).getFilesToBuild()) {
        PathFragment packageFragment =
            file.getArtifactOwner().getLabel().getPackageIdentifier().getSourceRoot();
        PathFragment packageRelativePath = file.getRootRelativePath().relativeTo(packageFragment);
        if (packageRelativePath.startsWith(assetsDir)) {
          PathFragment relativePath = packageRelativePath.relativeTo(assetsDir);
          PathFragment path = file.getExecPath();
          assetRoots.add(path.subFragment(0, path.segmentCount() - relativePath.segmentCount()));
        } else {
          ruleContext.attributeError(
              ASSETS_ATTR,
              String.format(
                  "'%s' (generated by '%s') is not beneath '%s'",
                  file.getRootRelativePath(), target.getLabel(), assetsDir));
          throw new RuleErrorException();
        }
        assets.add(file);
      }
    }

    return new AndroidAssets(assets.build(), assetRoots.build());
  }

  private static PathFragment getAssetDir(RuleContext ruleContext) {
    return PathFragment.create(ruleContext.attributes().get(ASSETS_DIR_ATTR, Type.STRING));
  }

  /**
   * Creates a {@link AndroidAssets} containing all the assets in a directory artifact, for use with
   * AarImport rules.
   *
   * <p>In general, {@link #from(RuleContext)} should be used instead, but it can't be for AarImport
   * since we don't know about its individual assets at analysis time.
   *
   * @param assetsDir the tree artifact containing a {@code assets/} directory
   */
  static AndroidAssets forAarImport(SpecialArtifact assetsDir) {
    Preconditions.checkArgument(assetsDir.isTreeArtifact());
    return new AndroidAssets(
        ImmutableList.of(assetsDir), ImmutableList.of(assetsDir.getExecPath().getChild("assets")));
  }

  static AndroidAssets empty() {
    return new AndroidAssets(ImmutableList.of(), ImmutableList.of());
  }

  private final ImmutableList<Artifact> assets;
  private final ImmutableList<PathFragment> assetRoots;

  AndroidAssets(AndroidAssets other) {
    this(other.assets, other.assetRoots);
  }

  @VisibleForTesting
  AndroidAssets(ImmutableList<Artifact> assets, ImmutableList<PathFragment> assetRoots) {
    this.assets = assets;
    this.assetRoots = assetRoots;
  }

  public ImmutableList<Artifact> getAssets() {
    return assets;
  }

  public ImmutableList<PathFragment> getAssetRoots() {
    return assetRoots;
  }

  public ParsedAndroidAssets parse(RuleContext ruleContext) throws InterruptedException {
    return ParsedAndroidAssets.parseFrom(ruleContext, this);
  }

  @Override
  public boolean equals(Object object) {
    if (object == null || getClass() != object.getClass()) {
      return false;
    }

    AndroidAssets other = (AndroidAssets) object;
    return assets.equals(other.assets) && assetRoots.equals(other.assetRoots);
  }

  @Override
  public int hashCode() {
    return Objects.hash(assets, assetRoots);
  }
}