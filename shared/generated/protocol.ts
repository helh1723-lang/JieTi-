// Generated from shared/protocol.schema.json. Do not edit.
export interface MemoryItem { category: string; scope: string; key: string; value: string; sourceIds: string[]; origin: string; confidence: number; observedAt: string; status: string; locked: boolean }
export interface MasterySnapshot { scope: string; concept: string; state: string; evidenceIds: string[]; observedAt: string }
export interface TeachingPolicyVersion { parentId: string; status: string; reason: string; instructions: string; evidenceIds: string[]; observations: number; negativeFeedback: number }
export interface PolicyEvaluation { policyId: string; passed: boolean; criticalViolations: number; baselineScore: number; candidateScore: number; caseIds: string[]; notes: string }
export interface ForgetRule { memoryId: string; scope: string; key: string; sourceIds: string[]; reason: string }
export interface Question { title: string; scope: string; concepts: string[]; transcription: string; studentWork: string; assetIds: string[]; classification: string; errorTypes: string[]; diagnosis: string; solution: string; status: string; classificationOrigin: string; userEditedFields: string[] }
export interface Message { questionId: string; role: string; content: string; mode: string; memoryIds: string[]; assetIds: string[]; policyId: string }
export interface Evidence { questionId: string; scope: string; concept: string; outcome: string; independent: boolean; hintLevel: number; sourceIds: string[] }
export interface Document { title: string; scope: string; assetIds: string[]; mime: string; status: string; pages: number; processed: number; error: string; paused: boolean; supplementalText: string }
export interface Citation { documentId: string; page: number; text: string; questionId: string }
export interface Artifact { questionId: string; mode: string; html: string; summary: string; status: string; assetIds: string[] }
export interface Note { questionId: string; content: string }
export interface Setting { key: string; value: string }
export type Kind = "memory"|"mastery"|"policy"|"evaluation"|"forget"|"question"|"message"|"evidence"|"document"|"citation"|"artifact"|"note"|"setting";
export interface SyncRecord {id:string;kind:Kind;rev:string;baseRev:string;payload:Record<string,unknown>;deleted:boolean;updatedAt:string;deviceId:string}
