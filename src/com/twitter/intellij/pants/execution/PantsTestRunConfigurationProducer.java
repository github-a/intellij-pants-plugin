// Copyright 2015 Pants project contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).

package com.twitter.intellij.pants.execution;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.TestIntegrationUtils;
import com.twitter.intellij.pants.model.PantsTargetAddress;
import com.twitter.intellij.pants.util.PantsConstants;
import com.twitter.intellij.pants.util.PantsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PantsTestRunConfigurationProducer extends RunConfigurationProducer<ExternalSystemRunConfiguration> {
  protected PantsTestRunConfigurationProducer() {
    super(PantsExternalTaskConfigurationType.getInstance());
  }

  @Override
  protected boolean setupConfigurationFromContext(
    @NotNull ExternalSystemRunConfiguration configuration,
    @NotNull ConfigurationContext context,
    @NotNull Ref<PsiElement> sourceElement
  ) {
    final Module module = context.getModule();
    if (module == null) {
      return false;
    }
    final VirtualFile buildRoot = PantsUtil.findBuildRoot(module);
    if (buildRoot == null) {
      return false;
    }
    final List<PantsTargetAddress> targets = PantsUtil.getTargetAddressesFromModule(module);
    if (targets.isEmpty()) {
      return false;
    }
    final PsiElement psiLocation = context.getPsiLocation();
    if (psiLocation == null) {
      return false;
    }

    final ExternalSystemTaskExecutionSettings taskSettings = configuration.getSettings();

    final List<String> scriptParameters = new ArrayList<>();
    /**
     * Add the module's folder:: to target_roots
     **/
    taskSettings.setExternalProjectPath(FileUtil.join(buildRoot.getPath(), targets.iterator().next().getPath()));
    taskSettings.setTaskNames(Collections.singletonList("test"));

    final PsiPackage testPackage;
    // Find out whether the click is on a test package
    if (psiLocation instanceof PsiPackage) {
      testPackage = (PsiPackage)psiLocation;
    }
    else if (psiLocation instanceof PsiDirectory) {
      testPackage = JavaDirectoryService.getInstance().getPackage(((PsiDirectory)psiLocation));
    }
    else {
      testPackage = null;
    }

    // Return false if it is a neither a test class nor a test package
    if (!TestIntegrationUtils.isTest(psiLocation) && !hasTestClasses(testPackage, module)) {
      return false;
    }

    // Obtain target address associated with this module.
    String serializedAddresses = module.getOptionValue(PantsConstants.PANTS_TARGET_ADDRESSES_KEY);
    if (serializedAddresses == null) {
      return false;
    }
    Set<String> targetAddresses = PantsUtil.filterGenTargets(PantsUtil.hydrateTargetAddresses(serializedAddresses));
    scriptParameters.addAll(targetAddresses);

    final PsiClass psiClass = TestIntegrationUtils.findOuterClass(psiLocation);
    final PsiMethod psiMethod = PsiTreeUtil.getParentOfType(psiLocation, PsiMethod.class, false);

    if (psiMethod != null) {
      sourceElement.set(psiMethod);
      configuration.setName(psiMethod.getName());
      scriptParameters.add("--test-junit-test=" + psiClass.getQualifiedName() + "#" + psiMethod.getName());
    }
    else if (psiClass != null) {
      sourceElement.set(psiClass);
      configuration.setName(psiClass.getName());
      scriptParameters.add("--test-junit-test=" + psiClass.getQualifiedName());
    }
    else if (testPackage != null) {
      sourceElement.set(testPackage);
      configuration.setName(testPackage.getName());
      // Iterate through test classes in testPackage that is only in the scope of the module
      Arrays.stream(testPackage.getClasses(module.getModuleScope()))
        .filter(TestIntegrationUtils::isTest)
        .forEach(psiClazz -> scriptParameters.add("--test-junit-test=" + psiClazz.getQualifiedName()));
    }
    else {
      return false;
    }

    taskSettings.setScriptParameters(StringUtil.join(scriptParameters, " "));
    return true;
  }

  private boolean hasTestClasses(PsiPackage psiPackage, Module module) {
    if (psiPackage == null) return false;
    for (PsiClass psiClass : psiPackage.getClasses(module.getModuleScope())) {
      if (TestIntegrationUtils.isTest(psiClass)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isConfigurationFromContext(
    @NotNull ExternalSystemRunConfiguration configuration,
    @NotNull ConfigurationContext context
  ) {
    final ExternalSystemRunConfiguration tempConfig = new ExternalSystemRunConfiguration(
      PantsConstants.SYSTEM_ID, context.getProject(), configuration.getFactory(), configuration.getName()
    );
    final Ref<PsiElement> locationRef = new Ref<PsiElement>(context.getPsiLocation());
    setupConfigurationFromContext(tempConfig, context, locationRef);
    return compareSettings(configuration.getSettings(), tempConfig.getSettings());
  }

  private boolean compareSettings(ExternalSystemTaskExecutionSettings settings1, ExternalSystemTaskExecutionSettings settings2) {
    if (!settings1.equals(settings2)) {
      return false;
    }

    if (!StringUtil.equalsIgnoreWhitespaces(settings1.getScriptParameters(), settings2.getScriptParameters())) {
      return false;
    }

    return true;
  }
}
