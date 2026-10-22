export type Role = 'STUDENT' | 'INSTRUCTOR' | 'ADMIN';
export interface Category {
  id: number;
  name: string;
  description?: string;
}
export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  bio?: string;
  phoneNumber?: string;
  avatarUrl?: string;
  role: Role;
  status: string;
  emailVerified: boolean;
  createdAt: string;
}
export interface Auth {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: User;
}
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  last: boolean;
}
export type Level = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'ALL_LEVELS';
export type LessonType = 'VIDEO' | 'ARTICLE' | 'QUIZ' | 'ASSIGNMENT';
export interface Lesson {
  id: string;
  title: string;
  content?: string;
  videoUrl?: string;
  durationSeconds?: number;
  type: LessonType;
  orderIndex: number;
  previewAllowed: boolean;
}
export interface Section {
  id: string;
  title: string;
  description?: string;
  orderIndex: number;
  lessons: Lesson[];
}
export interface Course {
  id: string;
  title: string;
  shortDescription: string;
  description?: string;
  thumbnailUrl?: string;
  previewVideoUrl?: string;
  instructorId?: string;
  instructorName: string;
  categoryName: string;
  status: string;
  level: Level;
  price: number;
  discountPrice?: number | null;
  language: string;
  durationMinutes?: number;
  requirements?: string[];
  outcomes?: string[];
  sections?: Section[];
  totalEnrollments: number;
  averageRating?: number;
  totalReviews: number;
  createdAt?: string;
}
export interface Review {
  id: string;
  studentId: string;
  studentName: string;
  studentAvatarUrl?: string;
  rating: number;
  comment?: string;
  createdAt: string;
}
export interface Enrollment {
  id: string;
  courseId: string;
  courseTitle: string;
  courseThumbnailUrl?: string;
  instructorName: string;
  status: string;
  pricePaid: number;
  totalLessons: number;
  completedLessons: number;
  progressPercentage: number;
  enrolledAt: string;
}
export interface Progress {
  enrollmentId: string;
  lessonId: string;
  sectionId: string;
  status: string;
  watchedSeconds: number;
  totalSeconds: number;
}
export interface Certificate {
  id: string;
  verificationCode: string;
  studentName: string;
  courseTitle: string;
  instructorName: string;
  issuedAt: string;
  pdfUrl?: string;
}
export interface Payment {
  id: string;
  courseId: string;
  courseTitle: string;
  amount: number;
  currency: string;
  status: string;
  receiptUrl?: string;
  createdAt: string;
}
export interface PaymentIntent {
  paymentId: string;
  clientSecret?: string | null;
  amount: number;
  currency: string;
  courseTitle: string;
}
export interface Refund {
  id: string;
  paymentId: string;
  amount: number;
  reason: string;
  status: string;
}
export interface Notification {
  id: string;
  subject: string;
  eventType: string;
  status: string;
  createdAt: string;
}
export interface Media {
  id: string;
  courseId?: string;
  lessonId?: string;
  type: 'IMAGE' | 'VIDEO';
  status: string;
  originalFileName: string;
  sizeBytes: number;
  durationSeconds?: number;
  streamUrl?: string;
  thumbnailUrl?: string;
  failureReason?: string;
}
