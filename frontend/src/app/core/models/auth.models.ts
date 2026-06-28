export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  nom: string;
  prenom: string;
  email: string;
  password: string;
  role?: string;
}

export interface UserResponse {
  id: string;
  nom: string;
  prenom: string;
  email: string;
  role: 'ADMIN' | 'DIRECTEUR' | 'COMPTABLE';
  actif: boolean;
  cabinetId?: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  type: string;
  user: UserResponse;
}
