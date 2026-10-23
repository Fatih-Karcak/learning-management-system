import { describe, it, expect } from 'vitest';
import { duplicateLesson } from './courseAuthoring';
import type { Workspace } from './types';

describe('copying a lesson', () => {
  it('keeps published identities and creates an independently editable quiz and resource list', () => {
    const workspace = {
      document: {
        course: {
          sections: [
            {
              id: 'section',
              lessons: [
                { id: 'quiz', title: 'Quiz', type: 'QUIZ', orderIndex: 0, previewAllowed: true },
              ],
            },
          ],
        },
        quizzes: {
          quiz: {
            passingScore: 70,
            questions: [
              { id: 'question', prompt: 'Original', options: ['A', 'B'], correctOption: 0 },
            ],
          },
        },
        materials: {
          quiz: [{ title: 'Guide', url: 'https://academy.test/guide', type: 'RESOURCE' }],
        },
      },
    } as unknown as Workspace;
    const id = duplicateLesson(workspace, 'quiz');
    const doc = workspace.document,
      lessons = doc.course.sections![0].lessons;
    expect(lessons.map((l) => l.id)).toEqual(['quiz', id]);
    expect(lessons[0].previewAllowed).toBe(true);
    expect(lessons[1].previewAllowed).toBe(false);
    expect(doc.quizzes[id].questions[0].id).not.toBe('question');
    doc.quizzes[id].questions[0].prompt = 'Edited copy';
    doc.materials[id][0].title = 'Edited guide';
    expect(doc.quizzes.quiz.questions[0].prompt).toBe('Original');
    expect(doc.materials.quiz[0].title).toBe('Guide');
  });
});
