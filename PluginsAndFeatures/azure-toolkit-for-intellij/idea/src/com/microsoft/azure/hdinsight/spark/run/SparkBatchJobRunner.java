/*
 * Copyright (c) Microsoft Corporation
 * <p/>
 * All rights reserved.
 * <p/>
 * MIT License
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
 * to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED *AS IS*, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package com.microsoft.azure.hdinsight.spark.run;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.DefaultProgramRunner;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import com.microsoft.azure.hdinsight.common.ClusterManagerEx;
import com.microsoft.azure.hdinsight.common.MessageInfoType;
import com.microsoft.azure.hdinsight.common.logger.ILogger;
import com.microsoft.azure.hdinsight.sdk.cluster.IClusterDetail;
import com.microsoft.azure.hdinsight.sdk.common.AzureSparkClusterManager;
import com.microsoft.azure.hdinsight.sdk.storage.HDStorageAccount;
import com.microsoft.azure.hdinsight.sdk.storage.IHDIStorageAccount;
import com.microsoft.azure.hdinsight.spark.common.*;
import com.microsoft.azure.hdinsight.spark.run.action.SparkBatchJobDisconnectAction;
import com.microsoft.azure.hdinsight.spark.run.configuration.RemoteDebugRunConfiguration;
import com.microsoft.azure.hdinsight.spark.ui.SparkJobLogConsoleView;
import com.microsoft.azuretools.authmanage.models.SubscriptionDetail;
import com.microsoft.intellij.rxjava.IdeaSchedulers;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import rx.Observer;
import rx.subjects.PublishSubject;

import java.io.IOException;
import java.util.AbstractMap.SimpleImmutableEntry;

public class SparkBatchJobRunner extends DefaultProgramRunner implements SparkSubmissionRunner, ILogger {
    @NotNull
    @Override
    public String getRunnerId() {
        return "SparkBatchJobRun";
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return SparkBatchJobRunExecutor.EXECUTOR_ID.equals(executorId) && profile.getClass() == RemoteDebugRunConfiguration.class;
    }

    @NotNull
    public ISparkBatchJob buildSparkBatchJob(@NotNull SparkSubmitModel submitModel,
                                             @NotNull Observer<SimpleImmutableEntry<MessageInfoType, String>> ctrlSubject) throws ExecutionException {
        // get storage account and access token from submitModel
        IHDIStorageAccount storageAccount = null;
        String accessToken = null;
        String normalizedAdlRootPath = null;
        IClusterDetail clusterDetail = ClusterManagerEx.getInstance().getClusterDetailByName(
                submitModel.getSubmissionParameter().getClusterName()).orElse(null);

        SparkSubmitStorageType storageAcccountType = submitModel.getJobUploadStorageModel().getStorageAccountType();
        switch (storageAcccountType) {
            case BLOB:
                String storageAccountName = submitModel.getJobUploadStorageModel().getStorageAccount();
                String fullStorageBlobName = ClusterManagerEx.getInstance().getBlobFullName(storageAccountName);
                String key = submitModel.getJobUploadStorageModel().getStorageKey();
                String container = submitModel.getJobUploadStorageModel().getSelectedContainer();
                storageAccount = new HDStorageAccount(clusterDetail, fullStorageBlobName, key, false, container);
                break;
            case DEFAULT_STORAGE_ACCOUNT:
                try {
                    clusterDetail.getConfigurationInfo();
                    storageAccount = clusterDetail.getStorageAccount();
                } catch (Exception ex) {
                    log().warn("Error getting cluster storage configuration. Error: " + ExceptionUtils.getStackTrace(ex));
                    storageAccount = null;
                }
                break;
            case SPARK_INTERACTIVE_SESSION:
                break;
            case ADLS_GEN1:
                String rawRootPath = submitModel.getJobUploadStorageModel().getAdlsRootPath();
                normalizedAdlRootPath = rawRootPath.endsWith("/") ? rawRootPath : rawRootPath + "/";
                // e.g. for adl://john.azuredatalakestore.net/root/path, adlsAccountName is john
                String adlsAccountName =  normalizedAdlRootPath.split("\\.")[0].split("//")[1];
                SubscriptionDetail subscriptionDetail =
                        AzureSparkClusterManager.getInstance().getSubscriptionDetailByStoreAccountName(adlsAccountName)
                                .toBlocking().singleOrDefault(null);
                if (subscriptionDetail == null) {
                    throw new ExecutionException(String.format("Error getting subscription info by ADLS root path. Please check if the ADLS account is %s's storage account", submitModel.getClusterName()));
                }
                // get Access Token
                try {
                    accessToken = AzureSparkClusterManager.getInstance().getAccessToken(subscriptionDetail.getTenantId());
                } catch (IOException ex) {
                    log().warn("Error getting access token based on the given ADLS root path. " + ExceptionUtils.getStackTrace(ex));
                    throw new ExecutionException("Error getting access token based on the given ADLS root path");
                }
                break;
        }

        return new SparkBatchJob(submitModel.getSubmissionParameter(), SparkBatchSubmission.getInstance(), ctrlSubject, storageAccount, accessToken, normalizedAdlRootPath);
    }

    @Nullable
    @Override
    protected RunContentDescriptor doExecute(@NotNull RunProfileState state,@NotNull ExecutionEnvironment environment) throws ExecutionException {
        SparkBatchRemoteRunProfileState submissionState = (SparkBatchRemoteRunProfileState) state;

        // Check parameters before starting
        submissionState.checkSubmissionParameter();

        SparkSubmitModel submitModel = submissionState.getSubmitModel();
        Project project = submitModel.getProject();

        // Prepare the run table console view UI
        SparkJobLogConsoleView jobOutputView = new SparkJobLogConsoleView(project);
        PublishSubject<SimpleImmutableEntry<MessageInfoType, String>> ctrlSubject = PublishSubject.create();
        SparkBatchJobRemoteProcess remoteProcess = new SparkBatchJobRemoteProcess(
                new IdeaSchedulers(project),
                buildSparkBatchJob(submitModel, ctrlSubject),
                submitModel.getArtifactPath().orElseThrow(() -> new ExecutionException("No artifact selected")),
                submitModel.getSubmissionParameter().getMainClassName(),
                ctrlSubject);
        SparkBatchJobRunProcessHandler processHandler = new SparkBatchJobRunProcessHandler(remoteProcess, "Package and deploy the job to Spark cluster", null);

        // After attaching, the console view can read the process inputStreams and display them
        jobOutputView.attachToProcess(processHandler);

        remoteProcess.start();
        SparkBatchJobDisconnectAction disconnectAction = new SparkBatchJobDisconnectAction(remoteProcess);

        ExecutionResult result = new DefaultExecutionResult(jobOutputView, processHandler, Separator.getInstance(), disconnectAction);
        submissionState.setExecutionResult(result);
        submissionState.setConsoleView(jobOutputView.getSecondaryConsoleView());
        submissionState.setRemoteProcessCtrlLogHandler(processHandler);

        ctrlSubject.subscribe(
                messageWithType -> {},
                err -> disconnectAction.setEnabled(false),
                () -> disconnectAction.setEnabled(false));

        return super.doExecute(state, environment);
    }
}
