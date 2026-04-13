import { api } from "./client";

export const authApi = {
  login: (data: { username: string; password: string }) =>
    api.post("/v2/auth/login", data),
  register: (data: { username: string; password: string; nickname?: string }) =>
    api.post("/v2/auth/register", data),
};

export const userApi = {
  getInfo: (userId: number) => api.get(`/user/${userId}`),
  update: (data: any) => api.post("/user/update", data),
  getStats: (userId: number) => api.get("/user/stats", { params: { userId } }),
  search: (query: string) => {
    const userId = localStorage.getItem("userId");
    return api.get("/friend/search", { params: { userId, keyword: query } });
  },
  recordGame: (userId: number, result: "win" | "lose" | "draw", gameMode: string, moves?: any[]) =>
    api.post("/user/game/record", { userId, result, gameMode, moves }),
};

export const roomApi = {
  create: (data: { name: string; maxPlayers: number }) => api.post("/room/create", data),
  join: (roomId: string) => api.post("/room/join", { roomId }),
  leave: (roomId: string) => api.post("/room/leave", { roomId }),
  list: () => api.get("/room/list"),
};

export const matchApi = {
  start: (mode: string) => api.post("/match/start", { mode }),
  cancel: (matchId: string) => api.post("/match/cancel", { matchId }),
};

export const gameApi = {
  getState: (roomId: string) => api.get(`/game/${roomId}/state`),
  surrender: (roomId: string) => api.post(`/game/${roomId}/surrender`),
  draw: (roomId: string) => api.post(`/game/${roomId}/draw`),
  undo: (roomId: string) => api.post(`/game/${roomId}/undo`),
};

export const friendApi = {
  list: (userId: number) => api.get("/friend/list", { params: { userId } }),
  request: (data: { userId: number; targetUserId: number; message?: string }) =>
    api.post("/friend/request", data),
  getRequests: (userId: number) => api.get("/friend/requests", { params: { userId } }),
  handleRequest: (requestId: number, userId: number, accept: boolean) =>
    api.post(accept ? "/friend/accept" : "/friend/reject", { userId, requestId }),
  delete: (userId: number, friendId: number) =>
    api.delete("/friend/delete", { params: { userId, friendId } }),
};

export const rankApi = {
  list: (type: "all" | "daily" | "weekly" | "monthly" = "all") => api.get(`/rank/${type}`),
  getUserRank: (type: string, userId: number) => api.get(`/rank/${type}/user/${userId}`),
};

export const recordApi = {
  list: (userId: number, filter: string = "all", limit: number = 20) =>
    api.get(`/v2/records/${userId}`, { params: { filter, limit } }),
  getDetail: (userId: number, gameId: string) => api.get(`/v2/records/${userId}/game/${gameId}`),
  getReplay: (gameId: string) => api.get(`/v2/replay/${gameId}`),
  getStats: (userId: number) => api.get("/api/record/stats", { params: { userId } }),
};

export const puzzleApi = {
  list: (difficulty: string = "easy") => api.get(`/puzzle/list?difficulty=${difficulty}`),
  getDetail: (puzzleId: number) => api.get(`/puzzle/${puzzleId}`),
  submit: (puzzleId: number, data: { userId: number; moves: number[][] }) =>
    api.post(`/puzzle/${puzzleId}/submit`, data),
  getStats: (userId: number) => api.get(`/puzzle/stats?userId=${userId}`),
};

export const observerApi = {
  getRooms: () => api.get("/observer/rooms"),
  join: (roomId: string, userId: number) => api.post("/observer/join", { roomId, userId }),
  leave: (roomId: string, userId: number) => api.post("/observer/leave", { roomId, userId }),
  getList: (roomId: string) => api.get(`/observer/${roomId}/observers`),
  getCount: (roomId: string) => api.get(`/observer/${roomId}/count`),
};
