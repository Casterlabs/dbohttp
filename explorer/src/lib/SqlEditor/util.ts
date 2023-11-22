export function renderHighlight(
	element: HTMLDivElement,
	colors: { [key: string]: string[] | RegExp }
) {
	const oldCursorPos = getCaretPosition(element);

	element.innerHTML = (element.innerText.match(/\w+|[^\w]/g) || [])
		.map((val) => {
			if (val == ' ') {
				return '<span>&nbsp;</span>';
			}

			for (const [color, filter] of Object.entries(colors)) {
				if (filter instanceof RegExp) {
					if (filter.test(val)) {
						return `<span style="color: ${color};">${escapeHtml(val)}</span>`;
					}
				} else {
					if (filter.includes(val)) {
						return `<span style="color: ${color};">${escapeHtml(val)}</span>`;
					}
				}
			}

			return `<span>${escapeHtml(val)}</span>`;
		})
		.join('');

	setCaretPosition(element, oldCursorPos);
}

function escapeHtml(input: string) {
	const doc = new DOMParser().parseFromString(input, 'text/html');
	return doc.body.textContent || '';
}

function getCaretPosition(element: HTMLDivElement): number {
	const selection = window.getSelection();
	if (selection == null || selection.rangeCount == 0) return -1;

	const range = selection.getRangeAt(0);
	const preCaretRange = range.cloneRange();
	preCaretRange.selectNodeContents(element);
	preCaretRange.setEnd(range.endContainer, range.endOffset);
	return preCaretRange.toString().length;
}

function setCaretPosition(element: Element, newPosition: number) {
	if (newPosition == -1) return;

	const { node, offset } = findTextNode(element, newPosition);
	const range = document.createRange();
	range.setStart(node, offset);
	range.collapse(true);
	const selection = window.getSelection();
	if (selection == null) return;
	selection.removeAllRanges();
	selection.addRange(range);
}

function findTextNode(element: Element, savedPosition: number) {
	let currentNode;
	let currentPosition = 0;
	const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT, null);

	while ((currentNode = walker.nextNode())) {
		const nodeLength = currentNode.textContent?.length || 0;
		if (currentPosition + nodeLength >= savedPosition) {
			return { node: currentNode, offset: savedPosition - currentPosition };
		}
		currentPosition += nodeLength;
	}

	// In case the position is beyond the text length, return the last node
	return { node: currentNode || element, offset: currentNode ? currentPosition : 0 };
}
