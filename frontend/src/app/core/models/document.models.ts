export interface DocumentResponse {
  id: string;
  nomFichier: string;
  nomOriginal?: string;
  typeDocument: string;
  taille: number;
  clientId?: string;
  cabinetId: string;
  uploadedBy: string;
  dateUpload: string;
  analyse: boolean;
  urlTelechargement?: string;
  minioObject?: string;
}

export interface DocumentUploadResponse {
  id: string;
  nomFichier: string;
  typeDocument: string;
  message: string;
}
