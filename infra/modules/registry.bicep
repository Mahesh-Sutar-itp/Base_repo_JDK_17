// Azure Container Registry for the application images. Images are pushed by the pipeline identity and pulled by
// the app identity (Entra ID tokens); the shared admin user stays disabled.

@description('Registry name: 5-50 letters and digits, unique across Azure.')
@minLength(5)
@maxLength(50)
param name string

@description('Azure region.')
param location string

@description('Tags for policy compliance and cost reporting.')
param tags object

resource registry 'Microsoft.ContainerRegistry/registries@2023-07-01' = {
  name: name
  location: location
  tags: tags
  sku: {
    name: 'Basic'
  }
  properties: {
    adminUserEnabled: false
    // Anonymous pull is off by default and not available on the Basic tier
    publicNetworkAccess: 'Enabled'
    zoneRedundancy: 'Disabled'
  }
}

output id string = registry.id
output name string = registry.name
output loginServer string = registry.properties.loginServer
