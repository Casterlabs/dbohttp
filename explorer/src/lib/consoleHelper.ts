export default function createConsole(name: string): Console {
	const consoleInstance = {
		...console,

		log() {
			console.log(...[`[${name}]`, ...arguments]);
		},

		warn() {
			console.warn(...[`[${name}]`, ...arguments]);
		},

		error() {
			console.error(...[`[${name}]`, ...arguments]);
		},

		info() {
			console.info(...[`[${name}]`, ...arguments]);
		},

		debug() {
			console.debug(...[`[${name}]`, ...arguments]);
		}
	};

	Object.freeze(consoleInstance);
	return consoleInstance;
}
