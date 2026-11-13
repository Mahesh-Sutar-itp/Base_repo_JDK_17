// Container Apps environment: the shared, network-integrated space the app runs in. It lives in the Container Apps
// subnet of the private network (so apps reach MySQL privately), has public (external) ingress for the HTTPS URL,
// and sends application and system logs to the Log Analytics workspace.

@description('Environment name.')
param name string

@description('Azure region (must be the region of the virtual network).')
param location string

@description('Tags for policy compliance and cost reporting.')
param tags object

@description('Resource id of the subnet delegated to Microsoft.App/environments.')
param infrastructureSubnetId string

@description('Name of the Log Analytics workspace in this resource group (it may be in another region).')
param logAnalyticsName string

@description('Name of the resource group that Azure creates and manages for the environment (load balancer, public IP).')
param infrastructureResourceGroupName string

resource workspace 'Microsoft.OperationalInsights/workspaces@2023-09-01' existing = {
  name: logAnalyticsName
}

resource environment 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: name
  location: location
  tags: tags
  properties: {
    // The workspace key is read by Azure Resource Manager during the deployment; it never appears in a file or output
    appLogsConfiguration: {
      destination: 'log-analytics'
      logAnalyticsConfiguration: {
        customerId: workspace.properties.customerId
        sharedKey: workspace.listKeys().primarySharedKey
      }
    }
    vnetConfiguration: {
      infrastructureSubnetId: infrastructureSubnetId
      // false = external: the environment gets a public endpoint (required for the public HTTPS URL)
      internal: false
    }
    // Workload profiles environment with the serverless Consumption profile only (no Dedicated profile, no fixed cost)
    workloadProfiles: [
      {
        name: 'Consumption'
        workloadProfileType: 'Consumption'
      }
    ]
    infrastructureResourceGroup: infrastructureResourceGroupName
    zoneRedundant: false
  }
}

output id string = environment.id
output defaultDomain string = environment.properties.defaultDomain
output staticIp string = environment.properties.staticIp
