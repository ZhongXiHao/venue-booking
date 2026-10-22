import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),

  routes: [
    {
      path: '/',
      redirect: '/venues',
    },
    {
      path: '/venues',
      name: 'venues',
      component: () =>
        import('../views/VenueListView.vue'),
    },
    {
      path: '/venues/new',
      name: 'venue-create',
      component: () =>
        import('../views/VenueCreateView.vue'),
    },
    {
      path: '/venues/:id',
      name: 'venue-detail',
      component: () =>
        import('../views/VenueDetailView.vue'),
    },
    {
      path: '/reservations',
      name: 'reservations',
      component: () =>
        import('../views/ReservationListView.vue'),
    },
    {
      path: '/reservations/new',
      name: 'reservation-create',
      component: () =>
        import('../views/ReservationCreateView.vue'),
    },
    {
      path: '/reservations/:id',
      name: 'reservation-detail',
      component: () =>
        import('../views/ReservationDetailView.vue'),
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () =>
        import('../views/NotFoundView.vue'),
    },
  ],
})

export default router