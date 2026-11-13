// Log Analytics workspace: central log store for Container Apps and, later, Application Insights.

@description('Workspace name.')
param name string

@description('Azure region.')
param location string

@description('Tags for policy compliance and cost reporting.')
param tags object

@description('Days of log retention (the first 31 days are included in the ingestion price).')
@minValue(30)
param retentionInDays int = 30

@description('Daily ingestion cap in GB, as a string because Bicep has no decimal type (for example "0.5").')
param dailyCapGb string = '0.5'

resource workspace 'Microsoft.OperationalInsights/workspaces@2023-09-01' = {
  name: name
  location: location
  tags: tags
  properties: {
    sku: {
      name: 'PerGB2018'
    }
    retentionInDays: retentionInDays
    // Hard stop for ingestion each day: protects the budget if something starts logging far too much
    workspaceCapping: {
      dailyQuotaGb: json(dailyCapGb)
    }
    features: {
      enableLogAccessUsingOnlyResourcePermissions: true
    }
  }
}

output id string = workspace.id
output name string = workspace.name
