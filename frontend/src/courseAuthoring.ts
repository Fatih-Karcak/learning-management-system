import type { Workspace } from './types';

export function duplicateLesson(workspace: Workspace, lessonId: string): string {
  const doc = workspace.document;
  const section = doc.course.sections?.find((s) => s.lessons.some((l) => l.id === lessonId));
  if (!section) throw new Error('Kopyalanacak ders bulunamadı.');
  const index = section.lessons.findIndex((l) => l.id === lessonId);
  const copy = structuredClone(section.lessons[index]);
  copy.id = crypto.randomUUID();
  copy.title = `${copy.title.slice(0, 185)} (kopya)`;
  copy.previewAllowed = false;
  section.lessons.splice(index + 1, 0, copy);
  section.lessons.forEach((l, i) => {
    l.orderIndex = i;
  });
  if (doc.quizzes[lessonId]) {
    const quiz = structuredClone(doc.quizzes[lessonId]);
    quiz.questions.forEach((q) => {
      q.id = crypto.randomUUID();
    });
    doc.quizzes[copy.id] = quiz;
  }
  if (doc.materials[lessonId]) doc.materials[copy.id] = structuredClone(doc.materials[lessonId]);
  return copy.id;
}
