import { lazy } from 'react';
import { createBrowserRouter, type RouteObject } from 'react-router';
import { FeatureRoute, ProtectedRoute, PublicOnlyRoute } from '@/components/auth/RouteGuards';
import { AppLayout } from '@/layouts/AppLayout';
import { AuthLayout } from '@/layouts/AuthLayout';
import { LoginPage } from '@/pages/auth/LoginPage';
import { RegisterPage } from '@/pages/auth/RegisterPage';
import { NotFoundPage, RouteErrorPage } from '@/pages/ErrorPages';
import type { Feature } from '@/lib/features';
import { LandingPage } from '@/pages/LandingPage';

// Signed-in pages are code-split; the charts library only loads with the dashboard.
const DashboardPage = lazy(() => import('@/pages/DashboardPage').then((m) => ({ default: m.DashboardPage })));
const ProductsPage = lazy(() => import('@/pages/ProductsPage').then((m) => ({ default: m.ProductsPage })));
const ProductDetailsPage = lazy(() => import('@/pages/ProductDetailsPage').then((m) => ({ default: m.ProductDetailsPage })));
const DocumentsPage = lazy(() => import('@/pages/DocumentsPage').then((m) => ({ default: m.DocumentsPage })));
const DocumentDetailPage = lazy(() => import('@/pages/DocumentDetailPage').then((m) => ({ default: m.DocumentDetailPage })));
const WarrantiesPage = lazy(() => import('@/pages/WarrantiesPage').then((m) => ({ default: m.WarrantiesPage })));
const ServicesPage = lazy(() => import('@/pages/ServicesPage').then((m) => ({ default: m.ServicesPage })));
const AssistantPage = lazy(() => import('@/pages/AssistantPage').then((m) => ({ default: m.AssistantPage })));
const NotificationsPage = lazy(() => import('@/pages/NotificationsPage').then((m) => ({ default: m.NotificationsPage })));
const GmailPage = lazy(() => import('@/pages/GmailPage').then((m) => ({ default: m.GmailPage })));
const ProfilePage = lazy(() => import('@/pages/ProfilePage').then((m) => ({ default: m.ProfilePage })));

/** A page that exists only once its backend feature does (see lib/features.ts). */
function gated(feature: Feature, route: RouteObject): RouteObject {
  return { element: <FeatureRoute feature={feature} />, children: [route] };
}

export const routes: RouteObject[] = [
  {
    errorElement: <RouteErrorPage />,
    children: [
      { path: '/', element: <LandingPage /> },
      {
        element: <PublicOnlyRoute />,
        children: [
          {
            element: <AuthLayout />,
            children: [
              { path: '/login', element: <LoginPage /> },
              { path: '/register', element: <RegisterPage /> },
            ],
          },
        ],
      },
      {
        element: <ProtectedRoute />,
        children: [
          {
            element: <AppLayout />,
            children: [
              gated('dashboard', { path: '/dashboard', element: <DashboardPage /> }),
              gated('products', { path: '/products', element: <ProductsPage /> }),
              gated('products', { path: '/products/:id', element: <ProductDetailsPage /> }),
              { path: '/documents', element: <DocumentsPage /> },
              { path: '/documents/:id', element: <DocumentDetailPage /> },
              gated('warranties', { path: '/warranties', element: <WarrantiesPage /> }),
              gated('services', { path: '/services', element: <ServicesPage /> }),
              gated('assistant', { path: '/assistant', element: <AssistantPage /> }),
              gated('notifications', { path: '/notifications', element: <NotificationsPage /> }),
              gated('gmail', { path: '/gmail', element: <GmailPage /> }),
              { path: '/profile', element: <ProfilePage /> },
            ],
          },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];

export function createAppRouter() {
  return createBrowserRouter(routes);
}
