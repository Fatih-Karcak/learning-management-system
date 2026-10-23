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
  adminScope?: 'FULL' | 'MODERATION' | 'SUPPORT';
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
  targetAudience?: string;
  id: string;
  title: string;
  shortDescription: string;
  reviewFeedback?: string;
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
  publishedAt?: string;
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
  lastAccessedAt?: string;
  completedAt?: string;
}
export interface Progress {
  enrollmentId: string;
  lessonId: string;
  sectionId: string;
  status: string;
  watchedSeconds: number;
  totalSeconds: number;
  positionSeconds?: number;
  watchedDurationSeconds?: number;
  lastWatchedAt?: string;
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

export interface QuizQuestion {
  id: string;
  prompt: string;
  options: string[];
  correctOption?: number;
  explanation?: string;
}
export interface Quiz {
  maxAttempts?: number;
  timeLimitSeconds?: number;
  questionCount?: number;
  randomize?: boolean;
  passingScore: number;
  questions: QuizQuestion[];
}
export interface QuizAttempt {
  id: string;
  score: number;
  passed: boolean;
  correct?: number;
  total?: number;
  createdAt?: string;
  feedback?: { questionId: string; correct: boolean; explanation?: string }[];
}
export interface AssignmentSubmission {
  id: string;
  studentId: string;
  studentName: string;
  enrollmentId: string;
  content: string;
  attachmentUrl?: string;
  status: 'SUBMITTED' | 'ACCEPTED' | 'CHANGES_REQUESTED';
  score?: number;
  feedback?: string;
  submittedAt: string;
  gradedAt?: string;
}
export interface LessonNote {
  id: string;
  lessonId: string;
  positionSeconds: number;
  content: string;
  updatedAt: string;
}
export interface CourseQuestion {
  id: string;
  lessonId?: string;
  authorId: string;
  authorName: string;
  title: string;
  content: string;
  acceptedReplyId?: string;
  replyCount: number;
  createdAt: string;
}
export interface QuestionReply {
  id: string;
  authorId: string;
  authorName: string;
  instructor: boolean;
  content: string;
  createdAt: string;
}
export interface RosterRow {
  enrollment: Enrollment;
  studentName: string;
  email: string;
}

export interface Material {
  title: string;
  url: string;
  type: 'RESOURCE' | 'SUBTITLE';
  language?: string;
}
export interface Workspace {
  revision: number;
  status: string;
  feedback?: string;
  document: {
    course: Course;
    categoryId: number;
    quizzes: Record<string, Quiz>;
    materials: Record<string, Material[]>;
  };
  checklist: string[];
}
