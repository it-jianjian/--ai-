import { createRouter, createWebHistory } from "vue-router";
import AuthPage from "./views/AuthPage.vue";
import MainLayout from "./views/MainLayout.vue";
import UserPage from "./views/UserPage.vue";
import HealthPage from "./views/HealthPage.vue";
import NotifyPage from "./views/NotifyPage.vue";
import AiConsultPage from "./views/AiConsultPage.vue";
import ReportPage from "./views/ReportPage.vue";
import KnowledgePage from "./views/KnowledgePage.vue";

const routes = [
  { path: "/auth", component: AuthPage },
  {
    path: "/",
    component: MainLayout,
    redirect: "/user",
    children: [
      { path: "user", component: UserPage, meta: { requiresAuth: true } },
      { path: "health", component: HealthPage, meta: { requiresAuth: true } },
      { path: "notify", component: NotifyPage, meta: { requiresAuth: true } },
      { path: "ai", component: AiConsultPage, meta: { requiresAuth: true } },
      { path: "report", component: ReportPage, meta: { requiresAuth: true } },
      { path: "knowledge", component: KnowledgePage, meta: { requiresAuth: true } }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to) => {
  const token = localStorage.getItem("zksh_token");
  if (to.meta.requiresAuth && !token) {
    return "/auth";
  }
  if (to.path === "/auth" && token) {
    return "/user";
  }
  return true;
});

export default router;
