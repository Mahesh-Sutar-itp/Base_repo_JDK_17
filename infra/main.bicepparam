// Parameters for infra/main.bicep. Private values (e-mail addresses, subscription id) are NOT in this public file:
// they are read from environment variables of the shell that runs the deployment. In PowerShell, before any
// az deployment command:
//   $env:TREBOL_OWNER_TAG       = 'value of the owner tag'
//   $env:TREBOL_BUDGET_EMAIL    = 'address that receives the budget alerts'
//   $env:TREBOL_SUBSCRIPTION_ID = 'subscription id (used to locate the Key Vault secret)'
using './main.bicep'

param location = 'centralindia'

param tags = {
  project: 'trebol-poc'
  owner: readEnvironmentVariable('TREBOL_OWNER_TAG')
  environment: 'poc'
  Practice: 'MLOps'
}

param logAnalyticsName = 'log-trebol-poc'
// 0.1 GB/day keeps the worst case at about 3 GB/month inside the 50 USD project total.
// Raise it temporarily (with approval) for the evaluation window, when the dashboard must show the load test.
param logAnalyticsDailyCapGb = '0.1'

// Project limit: 50 USD in total (1 Nov 2026 - 31 Oct 2027). Budget amounts are in the billing currency, and this
// subscription is billed in INR: 4,000 INR stays below 50 USD at any realistic exchange rate, so the alerts
// (50% = 2,000, 80% = 3,200, 100% = 4,000 INR) fire slightly early rather than late.
param budgetName = 'budget-trebol-poc'
param budgetAmount = 4000
// Full ISO format, as Azure stores it, so later what-if previews show no difference
param budgetStartDate = '2026-11-01T00:00:00Z'
param budgetEndDate = '2027-10-31T00:00:00Z'
param budgetAlertEmails = [
  readEnvironmentVariable('TREBOL_BUDGET_EMAIL')
]

// Stage 2: private network. 10.20.0.0/16 leaves room for later subnets.
param vnetName = 'vnet-trebol-poc'
param vnetAddressPrefix = '10.20.0.0/16'
// /23 = 512 addresses: room for 25 replicas plus old and new revisions side by side; cannot be resized later
param acaSubnetName = 'snet-aca'
param acaSubnetPrefix = '10.20.0.0/23'
param mysqlSubnetName = 'snet-mysql'
param mysqlSubnetPrefix = '10.20.2.0/24'
param mysqlPrivateDnsZoneName = 'trebol-poc.private.mysql.database.azure.com'

// Stage 3: identities (created on 2 Nov, now managed here), registry and Key Vault.
// Registry and vault names must be unique across Azure: fixed random suffix 'ttuu', chosen once, so the names stay
// the same if the resource group is moved to another subscription.
param pipelineIdentityName = 'id-trebol-github'
param appIdentityName = 'id-trebol-app'
param registryName = 'acrtrebolpocttuu'
param keyVaultName = 'kv-trebol-poc-ttuu'

// Stage 4: MySQL Flexible Server, Burstable B1ms (module defaults), private access only.
// The administrator password is read by Azure Resource Manager directly from Key Vault during the deployment
// (the vault is enabled for template deployment): it never appears in a shell, a file or the deployment history.
param mysqlServerName = 'mysql-trebol-poc-ttuu'
param mysqlVersion = '8.4'
param mysqlAdminLogin = 'trebol_admin'
param mysqlDatabaseName = 'trebol'
param mysqlAdminPassword = az.getSecret(readEnvironmentVariable('TREBOL_SUBSCRIPTION_ID'), 'rg-trebol-poc', 'kv-trebol-poc-ttuu', 'mysql-admin-password')
