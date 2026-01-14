import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'PeSIT Wizard',
  description: 'Solution moderne de transfert de fichiers bancaires',
  lang: 'fr-FR',
  
  // Ignore localhost links used in development examples
  ignoreDeadLinks: [
    /^http:\/\/localhost/,
    /^\/guide\/server\/clustering/
  ],
  
  head: [
    ['link', { rel: 'icon', href: '/favicon.ico' }]
  ],

  themeConfig: {
    logo: '/logo.svg',
    
    nav: [
      { text: 'Accueil', link: '/' },
      { text: 'Guide', link: '/guide/' },
      { text: 'API', link: '/api/' },
      { text: 'GitHub', link: 'https://github.com/pesitwizard/pesitwizard' }
    ],

    sidebar: {
      '/guide/': [
        {
          text: 'Introduction',
          items: [
            { text: 'Qu\'est-ce que PeSIT ?', link: '/guide/' },  // PeSIT est le protocole
            { text: 'Démarrage rapide', link: '/guide/quickstart' },
            { text: 'Architecture', link: '/guide/architecture' }
          ]
        },
        {
          text: 'PeSIT Wizard Client',
          items: [
            { text: 'Installation', link: '/guide/client/installation' },
            { text: 'Configuration', link: '/guide/client/configuration' },
            { text: 'Utilisation', link: '/guide/client/usage' },
            { text: 'Intégration ERP', link: '/guide/client/erp-integration' }
          ]
        },
        {
          text: 'PeSIT Wizard Server',
          items: [
            { text: 'Installation', link: '/guide/server/installation' },
            { text: 'Configuration', link: '/guide/server/configuration' },
            { text: 'Sécurité', link: '/guide/server/security' },
            { text: 'Gestion des secrets', link: '/guide/server/secrets' },
            { text: 'Connecteurs de stockage', link: '/guide/server/connectors' },
            { text: 'Observabilité', link: '/guide/server/observability' }
          ]
        }
      ],
      '/api/': [
        {
          text: 'API Reference',
          items: [
            { text: 'Vue d\'ensemble', link: '/api/' },
            { text: 'Authentification', link: '/api/authentication' },
            { text: 'Client API', link: '/api/client' },
            { text: 'Server API', link: '/api/server' }
          ]
        }
      ]
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/pesitwizard/pesitwizard' }
    ],

    footer: {
      message: 'PeSIT Wizard - Solution PeSIT moderne pour les entreprises',
      copyright: 'Copyright © 2025'
    },

    search: {
      provider: 'local'
    }
  }
})
