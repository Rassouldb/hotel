import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { authResponse, demoHotels, errorBody, installFakeApi, profile } from '../test/fakeApi'
import { renderApp } from '../test/renderApp'

afterEach(() => {
  vi.unstubAllGlobals()
})

const signedIn = (overrides = {}) => ({
  'POST /api/v1/auth/refresh': () => ({ status: 200, body: authResponse() }),
  'GET /api/v1/me': () => ({ status: 200, body: profile(overrides) }),
  'GET /api/v1/hotels': () => ({ status: 200, body: demoHotels }),
  'GET /actuator/health': () => ({ status: 200, body: { status: 'UP' } }),
})

describe('application', () => {
  it('sends anonymous visitors to the login page', async () => {
    installFakeApi({
      'POST /api/v1/auth/refresh': () => ({ status: 401, body: errorBody('SESSION_EXPIRED', 'Votre session a expiré.') }),
    })

    const { router } = renderApp('/administration')

    expect(await screen.findByRole('heading', { name: 'Connexion' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/connexion')
  })

  it('restores the session from the refresh cookie and shows the active hotel', async () => {
    installFakeApi(signedIn())

    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Tableau de bord' })).toBeInTheDocument()
    expect(screen.getByText('Bonjour Moussa')).toBeInTheDocument()
    expect(screen.getAllByText(/jeudi 1 octobre 2026/).length).toBeGreaterThan(0)
  })

  it('shows only the menu entries allowed by the user permissions', async () => {
    installFakeApi(signedIn())
    renderApp('/')

    await userEvent.click(await screen.findByRole('button', { name: 'Ouvrir le menu' }))
    const menu = await screen.findByRole('navigation', { name: 'Menu principal' })

    expect(menu).toHaveTextContent('Réservations')
    expect(menu).toHaveTextContent('Planning')
    expect(menu).not.toHaveTextContent('Administration')
    expect(menu).not.toHaveTextContent('Caisse')
  })

  it('gives a super administrator access to the administration', async () => {
    installFakeApi({
      ...signedIn({ superAdmin: true, hotels: [] }),
      'GET /api/v1/users': () => ({ status: 200, body: { success: true, data: [], meta: { page: 1, size: 25, total: 0 } } }),
      'GET /api/v1/roles': () => ({ status: 200, body: [] }),
    })

    renderApp('/administration/utilisateurs')

    expect(await screen.findByRole('tab', { name: 'Utilisateurs' })).toBeInTheDocument()
    expect(await screen.findByRole('tab', { name: "Paramètres de l'hôtel" })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Hôtels' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Rôles' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: "Journal d'audit" })).toBeInTheDocument()
    expect(await screen.findByText('Aucun utilisateur.')).toBeInTheDocument()
  })

  it('opens the hotel settings tab when it is requested directly', async () => {
    installFakeApi({
      ...signedIn({ superAdmin: true, hotels: [] }),
      'GET /api/v1/hotels/h1/settings': () => ({ status: 200, body: {} }),
    })

    const { router } = renderApp('/administration/parametres')

    expect(await screen.findByRole('tab', { name: 'Informations', selected: true })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/administration/parametres')
  })

  it('forces the replacement of a temporary password', async () => {
    installFakeApi(signedIn({ mustChangePassword: true }))

    const { router } = renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Choisissez votre mot de passe' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/changer-mot-de-passe')
  })

  it('logs in and opens the requested page', async () => {
    let loggedIn = false
    installFakeApi({
      ...signedIn(),
      'POST /api/v1/auth/refresh': () => (loggedIn
        ? { status: 200, body: authResponse() }
        : { status: 401, body: errorBody('SESSION_EXPIRED', 'Votre session a expiré.') }),
      'POST /api/v1/auth/login': (init) => {
        const body = JSON.parse(String(init?.body))
        if (body.password !== 'Teranga#2026') {
          return { status: 401, body: errorBody('INVALID_CREDENTIALS', "Nom d'utilisateur ou mot de passe incorrect.") }
        }
        loggedIn = true
        return { status: 200, body: authResponse() }
      },
    })
    const { router } = renderApp('/profil')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText(/Nom d'utilisateur/), 'reception')
    await user.type(screen.getByLabelText(/^Mot de passe/), 'mauvais')
    await user.click(screen.getByRole('button', { name: 'Se connecter' }))
    expect(await screen.findByText("Nom d'utilisateur ou mot de passe incorrect.")).toBeInTheDocument()

    await user.clear(screen.getByLabelText(/^Mot de passe/))
    await user.type(screen.getByLabelText(/^Mot de passe/), 'Teranga#2026')
    await user.click(screen.getByRole('button', { name: 'Se connecter' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/profil'))
    expect(await screen.findByRole('heading', { name: 'Mon profil' })).toBeInTheDocument()
  })

  it('explains when an account is locked', async () => {
    installFakeApi({
      'POST /api/v1/auth/refresh': () => ({ status: 401, body: errorBody('SESSION_EXPIRED', 'Votre session a expiré.') }),
      'POST /api/v1/auth/login': () => ({
        status: 423,
        body: errorBody('ACCOUNT_LOCKED', 'Compte bloqué.', [{ lockedUntil: '2026-10-01T10:15:00Z' }]),
      }),
    })
    renderApp('/connexion')
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText(/Nom d'utilisateur/), 'reception')
    await user.type(screen.getByLabelText(/^Mot de passe/), 'x')
    await user.click(screen.getByRole('button', { name: 'Se connecter' }))

    expect(await screen.findByText(/Compte bloqué jusqu’à/)).toBeInTheDocument()
  })
})
