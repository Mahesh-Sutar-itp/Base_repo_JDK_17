// Private network for the project: one subnet for the Container Apps environment, one for MySQL Flexible Server
// (private access, no public endpoint), and the private DNS zone that resolves the MySQL server name to its
// private address inside this network.

@description('Virtual network name.')
param name string

@description('Azure region.')
param location string

@description('Tags for policy compliance and cost reporting.')
param tags object

@description('Address space of the virtual network, for example 10.20.0.0/16.')
param addressPrefix string

@description('Subnet for the Container Apps environment (workload profiles environments need it delegated).')
param acaSubnetName string

@description('Address range of the Container Apps subnet. It cannot be resized once the environment uses it.')
param acaSubnetPrefix string

@description('Subnet for MySQL Flexible Server with private access (must be delegated to MySQL).')
param mysqlSubnetName string

@description('Address range of the MySQL subnet.')
param mysqlSubnetPrefix string

@description('Private DNS zone for MySQL; the name must end with .mysql.database.azure.com.')
param mysqlPrivateDnsZoneName string

// Subnets are declared inside the network (not as separate resources): a redeploy of the network then never
// tries to remove them, which would fail once a service uses them.
resource vnet 'Microsoft.Network/virtualNetworks@2024-05-01' = {
  name: name
  location: location
  tags: tags
  properties: {
    addressSpace: {
      addressPrefixes: [
        addressPrefix
      ]
    }
    subnets: [
      {
        name: acaSubnetName
        properties: {
          addressPrefix: acaSubnetPrefix
          delegations: [
            {
              name: 'Microsoft.App.environments'
              properties: {
                serviceName: 'Microsoft.App/environments'
              }
            }
          ]
        }
      }
      {
        name: mysqlSubnetName
        properties: {
          addressPrefix: mysqlSubnetPrefix
          delegations: [
            {
              name: 'Microsoft.DBforMySQL.flexibleServers'
              properties: {
                serviceName: 'Microsoft.DBforMySQL/flexibleServers'
              }
            }
          ]
        }
      }
    ]
  }
}

resource mysqlDnsZone 'Microsoft.Network/privateDnsZones@2024-06-01' = {
  name: mysqlPrivateDnsZoneName
  location: 'global'
  tags: tags
}

// Makes the zone visible to resources in the network (no automatic registration of VM names)
resource mysqlDnsLink 'Microsoft.Network/privateDnsZones/virtualNetworkLinks@2024-06-01' = {
  parent: mysqlDnsZone
  name: '${name}-link'
  location: 'global'
  tags: tags
  properties: {
    registrationEnabled: false
    virtualNetwork: {
      id: vnet.id
    }
  }
}

output vnetId string = vnet.id
output acaSubnetId string = '${vnet.id}/subnets/${acaSubnetName}'
output mysqlSubnetId string = '${vnet.id}/subnets/${mysqlSubnetName}'
output mysqlPrivateDnsZoneId string = mysqlDnsZone.id
