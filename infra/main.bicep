// Trebol POC infrastructure for one resource group (rg-trebol-poc, Central India).
// Deployed in stages; each stage adds resources to this file and is applied with one incremental deployment
// (PowerShell, with $sub holding the subscription id):
//   az deployment group what-if --subscription $sub --resource-group rg-trebol-poc --parameters infra/main.bicepparam
//   az deployment group create  --subscription $sub --resource-group rg-trebol-poc --parameters infra/main.bicepparam
// Stage 1: cost budget + Log Analytics workspace.
targetScope = 'resourceGroup'

@description('Azure region. Restricted to the region approved for this project.')
@allowed([
  'centralindia'
])
param location string

@description('Tags on every taggable resource. Policy requires "Practice".')
param tags object

@description('Log Analytics workspace name.')
param logAnalyticsName string

@description('Log Analytics daily ingestion cap in GB (string, for example "0.5").')
param logAnalyticsDailyCapGb string

@description('Budget name.')
param budgetName string

@description('Budget amount for the whole project, in the currency of the billing account.')
param budgetAmount int

@description('Budget start (first day of a month) and end dates, yyyy-MM-dd.')
param budgetStartDate string
param budgetEndDate string

@description('E-mail addresses for the budget alerts.')
param budgetAlertEmails array

module monitoring 'modules/monitoring.bicep' = {
  name: 'monitoring'
  params: {
    name: logAnalyticsName
    location: location
    tags: tags
    dailyCapGb: logAnalyticsDailyCapGb
  }
}

module budget 'modules/budget.bicep' = {
  name: 'budget'
  params: {
    name: budgetName
    amount: budgetAmount
    startDate: budgetStartDate
    endDate: budgetEndDate
    contactEmails: budgetAlertEmails
  }
}

output logAnalyticsWorkspaceId string = monitoring.outputs.id
output budgetId string = budget.outputs.id
