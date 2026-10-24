import type { Workspace } from './types';

export function duplicateLesson(workspace: Workspace, lessonId: string): string {
  const doc = workspace.document;
  const section = doc.course.sections?.find((s) => s.lessons.some((l) => l.id === lessonId));
  if (!section) throw new Error('The lesson to duplicate was not found.');
  const index = section.lessons.findIndex((l) => l.id === lessonId);
  const copy = structuredClone(section.lessons[index]);
  copy.id = crypto.randomUUID();
  copy.title = `${copy.title.slice(0, 185)} (copy)`;
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
  if (doc.assignments?.[lessonId]) {
    const policy = structuredClone(doc.assignments[lessonId]);
    policy.revision = 0;
    policy.rubric = policy.rubric.map((criterion) => ({ ...criterion, id: crypto.randomUUID() }));
    doc.assignments[copy.id] = policy;
  }
  return copy.id;
}
