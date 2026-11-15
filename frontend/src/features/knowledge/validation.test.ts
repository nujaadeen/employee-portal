import { driveFolderId, parseUrls, toggleRole, validateCode, validateDriveFolder, validateName, validatePdf } from './validation'
import { activityStatus, collectionStatus, sourceTypes, summarize } from './status'
import type { CollectionAdmin } from '@/api/types'

const MB = 1024 * 1024

describe('validatePdf', () => {
  it('accepts a PDF under the limit', () => { expect(validatePdf({ name: 'a.pdf', type: 'application/pdf', size: 2 * MB })).toBeNull() })
  it('accepts a PDF whose type the browser did not report', () => { expect(validatePdf({ name: 'A.PDF', type: '', size: 10 })).toBeNull() })
  it('refuses other types, empty files and files over 25 MB, before sending', () => {
    expect(validatePdf({ name: 'a.txt', type: 'text/plain', size: 10 })).toContain('not a PDF')
    expect(validatePdf({ name: 'a.pdf', type: 'application/pdf', size: 0 })).toContain('empty')
    expect(validatePdf({ name: 'big.pdf', type: 'application/pdf', size: 26 * MB })).toContain('The limit is 25 MB')
    expect(validatePdf({ name: 'edge.pdf', type: 'application/pdf', size: 25 * MB })).toBeNull()
  })
})

describe('new collection code', () => {
  it('follows the server rule', () => {
    expect(validateCode('partner-faq')).toBeNull()
    expect(validateCode('')).toBe('Enter a code.')
    expect(validateCode('ab')).toContain('3 to 40')
    for (const bad of ['Partner', '1abc', 'has_underscore', 'with space', 'x'.repeat(41)]) expect(validateCode(bad)).not.toBeNull()
    expect(validateCode('x'.repeat(40))).toBeNull()
    expect(validateName('  ')).toBe('Enter a name.')
    expect(validateName('Partner FAQ')).toBeNull()
  })
})

describe('Drive folder', () => {
  it('takes an id or the address of the folder', () => {
    expect(driveFolderId('https://drive.google.com/drive/folders/1AbCdEfGhIj?usp=sharing')).toBe('1AbCdEfGhIj')
    expect(driveFolderId(' 1AbCdEfGhIj ')).toBe('1AbCdEfGhIj')
    expect(validateDriveFolder('1AbCdEfGhIj')).toBeNull()
    expect(validateDriveFolder('')).toContain('Enter the folder ID')
    expect(validateDriveFolder('no spaces allowed')).toContain('letters, digits')
    expect(validateDriveFolder('abc')).not.toBeNull()
  })
})

describe('parseUrls', () => {
  it('reads one address per line and removes duplicates', () => {
    expect(parseUrls('https://baracel.example/\n\nhttps://baracel.example/products\nhttps://baracel.example/')).toEqual({ urls: ['https://baracel.example/', 'https://baracel.example/products'], error: null })
  })
  it('refuses other websites, other schemes and nonsense', () => {
    expect(parseUrls('https://a.example/\nhttps://b.example/').error).toContain('same website')
    expect(parseUrls('ftp://a.example/').error).toContain('http and https')
    expect(parseUrls('not a url').error).toContain('not a valid address')
    expect(parseUrls('  \n').error).toContain('at least one')
  })
})

describe('roles', () => {
  it('toggles a role, and ADMIN always stays', () => {
    expect(toggleRole(['SALES', 'ADMIN'], 'EMPLOYEE')).toEqual(['SALES', 'ADMIN', 'EMPLOYEE'])
    expect(toggleRole(['SALES', 'ADMIN'], 'SALES')).toEqual(['ADMIN'])
    expect(toggleRole(['SALES', 'ADMIN'], 'ADMIN')).toEqual(['SALES', 'ADMIN'])
    expect(toggleRole([], 'ADMIN')).toEqual(['ADMIN'])
  })
})

const col = (o: Partial<CollectionAdmin>): CollectionAdmin => ({ code: 'c', name: 'C', documents: 1, chunks: 1, status: 'READY', failedDocuments: 0, sourceTypes: [], roles: [], allEmployees: false, ...o })

describe('status', () => {
  it('shows words for every state', () => {
    expect(collectionStatus(col({}))).toEqual({ label: 'Ready', tone: 'success' })
    expect(collectionStatus(col({ status: 'INDEXING' })).label).toBe('Indexing')
    expect(collectionStatus(col({ failedDocuments: 2 }))).toEqual({ label: '2 files failed', tone: 'danger' })
    expect(collectionStatus(col({ failedDocuments: 1 })).label).toBe('1 file failed')
    expect(collectionStatus(col({ status: 'EMPTY' })).label).toBe('No documents')
    expect(collectionStatus(col({ status: 'UNKNOWN' })).label).toBe('Unknown')
    expect(activityStatus({ status: 'RUNNING', progress: 64 }).label).toBe('Indexing 64%')
    expect(activityStatus({ status: 'RUNNING', progress: 0 }).label).toBe('Indexing')
    expect(activityStatus({ status: 'DONE', progress: 100 }).label).toBe('Ready')
    expect(activityStatus({ status: 'FAILED', progress: 100 })).toEqual({ label: 'Failed', tone: 'danger' })
    expect(activityStatus({ status: 'PARTIAL', progress: 100 }).label).toBe('Some files failed')
  })
  it('names the source types and summarises for the cards', () => {
    expect(sourceTypes(['DRIVE', 'PDF', 'WEB'])).toBe('Drive, PDF, website')
    expect(sourceTypes([])).toBe('–')
    const s = summarize([col({ documents: 12, lastSync: '2026-11-15T09:12:00Z' }), col({ documents: 3, failedDocuments: 2 }), col({ documents: 0, status: 'FAILED', lastSync: '2026-11-14T08:00:00Z' })])
    expect(s).toEqual({ collections: 3, documents: 15, lastSync: '2026-11-15T09:12:00Z', needsAttention: 2, failedFiles: 2 })
    expect(summarize([]).lastSync).toBeNull()
  })
})
