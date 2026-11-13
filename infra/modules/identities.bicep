// The two user-assigned managed identities: one for the pipeline (deploys), one for the running app (pulls images,
// reads secrets). Their role assignments are made by the subscription admin (see infra/roles.bicep).

@description('Identity used by GitHub Actions through OIDC.')
param pipelineIdentityName string

@description('Identity used by the container app.')
param appIdentityName string

@description('Azure region.')
param location string

@description('Tags for policy compliance and cost reporting.')
param tags object

@description('GitHub repository allowed to log in, exactly as in the subject of GitHub\'s OIDC token: owner@ownerId/name@repoId.')
param githubRepository string

resource pipelineIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: pipelineIdentityName
  location: location
  tags: tags
}

// GitHub Actions logs in with OIDC: GitHub signs a short-lived token describing the workflow run, and Entra ID
// exchanges it for an Azure token only if issuer, audience and subject match one of these credentials.
// No password or client secret exists. Azure accepts only one write at a time per identity, hence dependsOn.
// GitHub's subject contains the numeric ids of the owner and the repository (owner@id/name@id), so a deleted and
// re-created account or repository with the same name cannot inherit this trust.
resource githubMain 'Microsoft.ManagedIdentity/userAssignedIdentities/federatedIdentityCredentials@2023-01-31' = {
  parent: pipelineIdentity
  name: 'github-main'
  properties: {
    issuer: 'https://token.actions.githubusercontent.com'
    // Runs on the main branch: pushes (deployments), scheduled and manual runs
    subject: 'repo:${githubRepository}:ref:refs/heads/main'
    audiences: [
      'api://AzureADTokenExchange'
    ]
  }
}

resource githubPullRequest 'Microsoft.ManagedIdentity/userAssignedIdentities/federatedIdentityCredentials@2023-01-31' = {
  parent: pipelineIdentity
  name: 'github-pull-request'
  dependsOn: [
    githubMain
  ]
  properties: {
    issuer: 'https://token.actions.githubusercontent.com'
    // Pull request runs (the workflow only logs in for pull requests from branches of this repository)
    subject: 'repo:${githubRepository}:pull_request'
    audiences: [
      'api://AzureADTokenExchange'
    ]
  }
}

resource appIdentity 'Microsoft.ManagedIdentity/userAssignedIdentities@2023-01-31' = {
  name: appIdentityName
  location: location
  tags: tags
}

output pipelineIdentityId string = pipelineIdentity.id
output pipelinePrincipalId string = pipelineIdentity.properties.principalId
output pipelineClientId string = pipelineIdentity.properties.clientId
output appIdentityId string = appIdentity.id
output appPrincipalId string = appIdentity.properties.principalId
output appClientId string = appIdentity.properties.clientId
