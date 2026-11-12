// Parameters for infra/main.bicep. Private values (e-mail addresses) are NOT in this public file: they are read
// from environment variables of the shell that runs the deployment. In PowerShell, before any az deployment command:
//   $env:TREBOL_OWNER_TAG    = 'value of the owner tag'
//   $env:TREBOL_BUDGET_EMAIL = 'address that receives the budget alerts'
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
param budgetStartDate = '2026-11-01'
param budgetEndDate = '2027-10-31'
param budgetAlertEmails = [
  readEnvironmentVariable('TREBOL_BUDGET_EMAIL')
]
