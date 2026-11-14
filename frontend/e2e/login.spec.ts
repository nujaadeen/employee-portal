import { expect, test } from '@playwright/test'
import { shot } from './helpers'

test('the Keycloak login page is the Baracel theme', async ({ page }) => {
  await page.goto('/')
  await page.waitForURL(/\/realms\/baracel\//)
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Continue with Google' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Continue with GitHub' })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Continue with Facebook' })).toBeVisible()
  await expect(page.getByLabel('Work email')).toBeVisible()
  await expect(page.getByText('wait for HR approval')).toBeVisible()
  await shot(page, '01-login')
  await page.getByRole('link', { name: 'Create account' }).click()
  await expect(page.getByRole('heading', { name: 'Create your account' })).toBeVisible()
  await shot(page, '01-register')
  await page.goto('/')
  await page.getByRole('link', { name: 'Forgot password?' }).click()
  await expect(page.getByRole('heading', { name: 'Forgot your password?' })).toBeVisible()
  await shot(page, '01-forgot-password')
  // wrong password shows the Keycloak error in the card
  await page.goto('/')
  await page.locator('#username').fill('aisha.rahman')
  await page.locator('#password').fill('wrong password')
  await page.locator('#kc-login').click()
  await expect(page.getByRole('alert')).toContainText(/Invalid/i)
  await shot(page, '01-login-error')
})
