// Key Vault for the application secrets (database password, JWT signing key). Access is controlled by Azure roles
// (RBAC), not vault access policies. The engineer writes secrets; the app identity can only read them.

@description('Vault name: 3-24 letters, digits and hyphens, unique across Azure.')
@minLength(3)
@maxLength(24)
param name string

@description('Azure region.')
param location string

@description('Tags for policy compliance and cost reporting.')
param tags object

resource vault 'Microsoft.KeyVault/vaults@2023-07-01' = {
  name: name
  location: location
  tags: tags
  properties: {
    tenantId: subscription().tenantId
    sku: {
      family: 'A'
      name: 'standard'
    }
    enableRbacAuthorization: true
    // A deleted vault stays recoverable for 7 days and cannot be purged early (purge protection is irreversible)
    enableSoftDelete: true
    softDeleteRetentionInDays: 7
    enablePurgeProtection: true
    // Lets deployments read a secret through the parameter file (getSecret), so the database password never
    // appears on a command line
    enabledForTemplateDeployment: true
    enabledForDeployment: false
    enabledForDiskEncryption: false
    // Network restrictions come later, once the app reads its secrets and access can be tested
    publicNetworkAccess: 'Enabled'
    networkAcls: {
      defaultAction: 'Allow'
      bypass: 'AzureServices'
    }
  }
}

output id string = vault.id
output name string = vault.name
output uri string = vault.properties.vaultUri
