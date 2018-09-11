package com.microsoft.intellij.runner.webapp

import com.jetbrains.rider.model.PublishableProjectModel
import com.microsoft.azure.management.appservice.OperatingSystem
import com.microsoft.azure.management.appservice.PricingTier
import com.microsoft.azure.management.appservice.RuntimeStack
import com.microsoft.azure.management.resources.fluentcore.arm.Region
import com.microsoft.azure.management.sql.SqlDatabase

class AzureDotNetWebAppSettingModel {
    var subscriptionId = ""

    var isCreatingWebApp = false
    var webAppId = ""
    var webAppName = ""

    var isCreatingResourceGroup = false
    var resourceGroupName = ""

    var isCreatingAppServicePlan = false
    var appServicePlanId = ""
    var appServicePlanName = ""
    var operatingSystem = defaultOperatingSystem
    var location = defaultLocation
    var pricingTier = defaultPricingTier

    var runtime = defaultRuntime

    var isDatabaseConnectionEnabled = false
    var connectionStringName = ""
    var database: SqlDatabase? = null
    var sqlDatabaseAdminLogin = ""
    var sqlDatabaseAdminPassword = charArrayOf()

    var publishableProject: PublishableProjectModel? = null

    companion object {
        val defaultOperatingSystem = OperatingSystem.WINDOWS
        val defaultPricingTier: PricingTier = PricingTier.STANDARD_S1
        val defaultLocation: String = Region.US_EAST.name()
        val defaultRuntime = RuntimeStack("DOTNETCORE", "2.1")
    }
}