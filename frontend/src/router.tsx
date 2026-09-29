import { lazy } from 'react';
import { createBrowserRouter, type RouteObject } from 'react-router';
import { ProtectedRoute, PublicOnlyRoute } from '@/components/auth/RouteGuards';
import { AppLayout } from '@/layouts/AppLayout';
import { AuthLayout } from '@/layouts/AuthLayout';
import { LoginPage } from '@/pages/auth/LoginPage';
import { RegisterPage } from '@/pages/auth/RegisterPage';
import { NotFoundPage, RouteErrorPage } from '@/pages/ErrorPages';
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
              { path: '/dashboard', element: <DashboardPage /> },
              { path: '/products', element: <ProductsPage /> },
              { path: '/products/:id', element: <ProductDetailsPage /> },
              { path: '/documents', element: <DocumentsPage /> },
              { path: '/documents/:id', element: <DocumentDetailPage /> },
              { path: '/warranties', element: <WarrantiesPage /> },
              { path: '/services', element: <ServicesPage /> },
              { path: '/assistant', element: <AssistantPage /> },
              { path: '/notifications', element: <NotificationsPage /> },
              { path: '/gmail', element: <GmailPage /> },
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
