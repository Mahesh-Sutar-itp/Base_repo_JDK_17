// Cost budget on the resource group. A budget only sends alerts: it never stops or limits spending.
// The real controls are the working-hours schedule, scale limits and the Log Analytics daily cap.

@description('Budget name.')
param name string

@description('Total amount for the budget period, in the currency of the billing account.')
@minValue(1)
param amount int

@description('First day of the budget (must be the first day of a month).')
param startDate string

@description('Last day of the budget.')
param endDate string

@description('E-mail addresses that receive the alerts.')
@minLength(1)
param contactEmails array

// One period for the whole project: with an annual time grain and a 12-month window the amount is a project total
// (a monthly grain would allow the full amount again every month).
resource budget 'Microsoft.Consumption/budgets@2023-11-01' = {
  name: name
  properties: {
    category: 'Cost'
    amount: amount
    timeGrain: 'Annually'
    timePeriod: {
      startDate: startDate
      endDate: endDate
    }
    notifications: {
      actual50: {
        enabled: true
        operator: 'GreaterThanOrEqualTo'
        threshold: 50
        thresholdType: 'Actual'
        contactEmails: contactEmails
      }
      actual80: {
        enabled: true
        operator: 'GreaterThanOrEqualTo'
        threshold: 80
        thresholdType: 'Actual'
        contactEmails: contactEmails
      }
      actual100: {
        enabled: true
        operator: 'GreaterThanOrEqualTo'
        threshold: 100
        thresholdType: 'Actual'
        contactEmails: contactEmails
      }
      // Early warning: Azure's cost forecast predicts the total will reach the full amount
      forecast100: {
        enabled: true
        operator: 'GreaterThanOrEqualTo'
        threshold: 100
        thresholdType: 'Forecasted'
        contactEmails: contactEmails
      }
    }
  }
}

output id string = budget.id
